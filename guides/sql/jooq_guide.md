---
name: sql-jooq
description: >
  Using compile-time safe database queries, hybrid CQRS patterns, and direct record-to-DTO projection with jOOQ.
---

# jOOQ & Compile-Time Safe Persistence Architecture Guide

This guide establishes production standards for SQL-first persistence: leveraging jOOQ for compile-time verified database querying, high-speed direct DTO projections, and designing hybrid CQRS persistence architectures.

---

## 1. The Compile-Time Safe Query Invariant (jOOQ DSL)

In standard JPA / JPQL or raw JDBC queries, SQL strings are evaluated purely at runtime. If a database column is renamed or dropped during schema migration (`Flyway` / `Liquibase`), standard applications compile successfully but crash in production with fatal `SQLGrammarException` runtime errors.

```text
+---------------------------------------------------------------------------------------+
|                    String JPQL vs. Compile-Time Safe jOOQ DSL                         |
+---------------------------------------------------------------------------------------+
| ❌ String-Based JPQL / JDBC (Runtime Crash Risk):                                     |
| entityManager.createQuery("SELECT u FROM User u WHERE u.firstName = :name");          |
| (If DB column 'first_name' is renamed to 'given_name', compiler is blind!)            |
|                                                                                       |
| ✅ jOOQ Generated DSL (Absolute Compile-Time Safety):                                  |
| dsl.selectFrom(USERS).where(USERS.GIVEN_NAME.eq(name)).fetch();                       |
| (If DB column changes, generated Java classes change ---> Instant CI Compiler Error!) |
+---------------------------------------------------------------------------------------+
```

#### ORM Runtime Failure vs. jOOQ Compile-Time Safety
Consider a schema migration renaming a column:
```sql
ALTER TABLE orders RENAME COLUMN total_amount TO amount;
```
* **Hibernate Risk**: The `@Column(name = "total_amount")` mapping remains in the entity code. The application compiles, packages, and deploys. The error triggers only at runtime when the field is accessed under load.
* **jOOQ Compile-Time Guarantee**: After regeneration, the generated class `Orders.TOTAL_AMOUNT` no longer exists, and the compiler instantly rejects any referencing query code, blocking the build from deployment.

#### Explicit SQL Ownership
Writing queries with jOOQ forces developers to explicitly state columns, filters, and sort options. This eliminates "implicit query magic" and guarantees that the SQL reviewed during code review matches the SQL run in production.

```java
public List<OrderSummary> fetchPaidOrders(long customerId) {
    return dsl.select(ORDERS.ID, ORDERS.AMOUNT, ORDERS.CREATED_AT)
              .from(ORDERS)
              .where(ORDERS.CUSTOMER_ID.eq(customerId))
              .and(ORDERS.STATUS.eq("PAID"))
              .orderBy(ORDERS.CREATED_AT.desc())
              .fetchInto(OrderSummary.class);
}
```

---

## 2. Direct DTO Projections (Zero Reflection)

When reading data for REST API endpoints, fetching heavy JPA managed entities only to immediately map them to DTOs causes severe memory allocation thrash and dirty-checking overhead. jOOQ enables direct, reflection-free mapping into immutable Java Records, resulting in lower heap usage and reduced Garbage Collection (GC) pauses.

```java
public record UserSummaryDto(Long id, String fullName, String email) {}

@Repository
@RequiredArgsConstructor
public class JooqUserRepository {
    private final DSLContext dsl;

    public List<UserSummaryDto> fetchActiveUserSummaries(Long tenantId) {
        return dsl.select(
                USERS.ID,
                concat(USERS.FIRST_NAME, val(" "), USERS.LAST_NAME).as("fullName"),
                USERS.EMAIL
            )
            .from(USERS)
            .where(USERS.TENANT_ID.eq(tenantId))
            .and(USERS.ACTIVE.isTrue())
            .fetchInto(UserSummaryDto.class); // High-speed direct projection!
    }
}
```

---

## 3. Query Plan Predictability: Resolving ORM Side-Effects

Databases do not execute Java objects; they execute physical query plans. In production, query plan stability is crucial for maintaining predictable latency SLAs.

#### ❌ The ORM Implicit Query Plan Trap
Accessing mapped collection relationships can cause Hibernate to generate complex joins or execute unexpected lazy-loading $N+1$ queries.
```java
// Accessing items on a JPA Entity
Order order = entityManager.find(Order.class, id);
order.getItems().size(); // Triggers lazy load
```
This triggers an unexpected SQL join:
```sql
SELECT o.*, i.* FROM orders o LEFT JOIN order_items i ON o.id = i.order_id WHERE o.id = ?;
```
Under load, this query can alter the database execution plan, causing a high-cost sequential scan:
```text
Nested Loop (cost=0.86..12435.22 rows=500 width=256)
  -> Index Scan using orders_pkey on orders o
  -> Seq Scan on order_items i  <-- Sequential Scan triggered by join cardinality explosion
```

#### ✅ The jOOQ Predictable Plan Invariant
With jOOQ, the SQL matches the written DSL exactly, preventing hidden joins:
```java
dsl.select(ORDERS.ID, ORDERS.AMOUNT)
   .from(ORDERS)
   .where(ORDERS.ID.eq(orderId))
   .fetchOne();
```
```sql
SELECT id, amount FROM orders WHERE id = ?;
```
Ensures a lightweight, stable database execution plan:
```text
Index Scan using orders_pkey on orders (cost=0.29..8.31 rows=1 width=32)
```

---

## 4. Hybrid CQRS Persistence Architecture (jOOQ + Hibernate)

Mature enterprise systems balance write safety and read speed by partitioning read and write data pathways using a hybrid CQRS (Command Query Responsibility Segregation) model.

```text
+---------------------------------------------------------------------------------------+
|                            Hybrid CQRS Architecture                                   |
+---------------------------------------------------------------------------------------+
| [ Incoming REST API Request ]                                                         |
|             |                                                                         |
|             +---> Read Query (GET /orders) ---> [ jOOQ DSL ] ---> Direct DTO Record   |
|             |                                    (0 Proxy Overhead, Stable Plans)     |
|             |                                                                         |
|             +---> Write Command (POST) ---> [ Spring Data JPA ] ---> Managed Entity   |
|                                                  (Optimistic Locking, Lifecycle hook) |
+---------------------------------------------------------------------------------------+
```

#### CQRS Partitioning Rules
1. **Command Pathway (Writes - JPA/Hibernate)**: Use entity models to enforce business rules, cascade relationships, handle dirty checks, check optimistic locking (`@Version`), and automate audits (`@CreatedDate`).
2. **Query Pathway (Reads - jOOQ)**: Use jOOQ DSL for fast read operations, reporting, complex multi-table joins, and direct record-to-DTO projections.

```java
// WRITE PATHWAY (JPA)
@Service
@Transactional
@RequiredArgsConstructor
public class OrderCommandService {
    private final EntityManager entityManager;

    public void createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        entityManager.persist(order); // Leverages dirty checks & entity lifecycle
    }
}

// READ PATHWAY (jOOQ)
@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {
    private final DSLContext dsl;

    public OrderView fetchOrder(long orderId) {
        return dsl.select(ORDERS.ID, ORDERS.AMOUNT, ORDERS.STATUS)
                  .from(ORDERS)
                  .where(ORDERS.ID.eq(orderId))
                  .fetchOneInto(OrderView.class); // Explicit columns, predictable performance
    }
}
```

---

## 5. Production Diagnostics & Incident Prevention

### 1. 2:00 AM Incident Diagnostic Clarity
When debugging query failures under load, jOOQ outputs the exact SQL with inline bind values:
```properties
logging.level.org.jooq=DEBUG
```
This prints copy-pasteable SQL ready to run directly against a database console for rapid `EXPLAIN` analysis.

### 2. When to Avoid jOOQ
* **Simple CRUD Prototypes**: If the application consists only of single-table inserts and primary-key lookups, JPA/Hibernate repositories require less initial setup.
* **Lack of SQL Expertise**: jOOQ is SQL-first. Teams must understand execution plans, indexing, and join optimization to leverage its benefits.

---

## 6. jOOQ Verification Checklist

```text
[ ] 1. Schema Sync      : Verify build pipelines automatically regenerate jOOQ DSL classes upon Flyway migrations.
[ ] 2. Direct Projection: Audit high-throughput GET endpoints to ensure direct record projection over entity fetching.
[ ] 3. CQRS Partitioning: Enforce strict separation between JPA write entities and jOOQ read repositories.
[ ] 4. No Implicit Joins: Avoid fetch-joins or lazy-loading side-effects on read paths; write explicit, flat SQL queries.
[ ] 5. Bind Logging     : Configure org.jooq log levels to output plain copy-pasteable SQL during debugging.
```
