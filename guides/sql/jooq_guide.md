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

---

## 2. Direct DTO Projections (Zero Reflection)

When reading data for REST API endpoints, fetching heavy JPA managed entities only to immediately map them to DTOs causes severe memory allocation thrash and dirty-checking overhead. jOOQ enables direct, reflection-free mapping into immutable Java Records.

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

## 3. Hybrid CQRS Persistence Architecture (jOOQ + Hibernate)

Enterprise architectures achieve optimal balance by partitioning read and write data pathways using a hybrid CQRS (Command Query Responsibility Segregation) model.

```text
+---------------------------------------------------------------------------------------+
|                            Hybrid CQRS Architecture                                   |
+---------------------------------------------------------------------------------------+
| [ Incoming REST API Request ]                                                         |
|             |                                                                         |
|             +---> Read Query (GET /users) ---> [ jOOQ DSL ] ---> Direct DTO Record    |
|             |                                    (0 Proxy Overhead, 10x Speed!)       |
|             |                                                                         |
|             +---> Write Command (POST) ---> [ Spring Data JPA ] ---> Managed Entity   |
|                                                  (Rich Domain Validation & Auditing)  |
+---------------------------------------------------------------------------------------+
```

### Architectural Rules
1. **Command Pathway (Writes):** Use Spring Data JPA / Hibernate entity models to enforce rich domain validation rules, optimistic locking (`@Version`), and automated auditing (`@CreatedDate`).
2. **Query Pathway (Reads):** Use jOOQ DSL or lightweight `JdbcTemplate` for high-throughput reporting, complex multi-table joins, and direct DTO projections.

---

## 4. jOOQ Verification Checklist

```text
[ ] 1. Schema Sync      : Verify build pipelines automatically regenerate jOOQ DSL classes upon Flyway migrations.
[ ] 2. Direct Projection: Audit high-throughput GET endpoints to ensure direct record projection over entity fetching.
[ ] 3. CQRS Partitioning: Enforce strict separation between JPA write entities and jOOQ read repositories.
```
