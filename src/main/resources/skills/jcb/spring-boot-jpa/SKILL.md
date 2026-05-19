---
name: spring-boot-jpa
description: >
  JPA/Hibernate best practices: preserving JDBC batching via SEQUENCE IDs and implementing constant-time O(1) keyset pagination.
---

# Spring Boot JPA & Persistence Architecture Guide

This guide establishes standards for Spring Data JPA persistence: optimizing primary key generation for high-speed JDBC batching and implementing constant-time $O(1)$ keyset cursor pagination over massive datasets.

---

## 1. Primary Key Generation & JDBC Batching

When persisting large volumes of entities, high-performance applications must group inserts into multi-row JDBC batches (`INSERT INTO table (id, val) VALUES (1, 'A'), (2, 'B')`).

```text
+---------------------------------------------------------------------------------------+
|                    IDENTITY Generation vs. SEQUENCE Batching Mechanics                |
+---------------------------------------------------------------------------------------+
| ❌ GenerationType.IDENTITY (Disables JDBC Batching):                                  |
| DB must assign ID upon insert. Hibernate cannot batch because entity ID is unknown!   |
|                                                                                       |
| ✅ GenerationType.SEQUENCE with pooled hi/lo allocator:                               |
| Fetches 50 IDs upfront ---> Executes single batched JDBC multi-insert (10x faster!)   |
+---------------------------------------------------------------------------------------+
```

### The Identity Batching Pitfall
When an entity primary key is configured with `GenerationType.IDENTITY`, Hibernate is forced to execute an immediate, standalone `INSERT` statement upon calling `entityManager.persist()`. This completely disables JDBC batching across the persistence unit.

```java
// ✅ High-Speed Batching Entity Architecture
@Entity
@Table(name = "enterprise_orders")
public class EnterpriseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(
        name = "order_seq",
        sequenceName = "seq_enterprise_orders",
        allocationSize = 50 // Pre-allocates 50 IDs in JVM memory per database sequence hit
    )
    private Long id;

    @Column(nullable = false)
    private String orderNumber;
}
```

### Enabling JDBC Batching in Spring Boot Properties
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

---

## 2. Constant-Time $O(1)$ Keyset Cursor Pagination

### ❌ The Offset Pagination Catastrophe
```sql
-- ❌ Offset Pagination (O(N) Degradation):
-- Database must traverse 1,000,000 rows, discard them, and fetch the next 50!
SELECT * FROM orders ORDER BY created_at DESC LIMIT 50 OFFSET 1000000;
```

### ✅ The Keyset (Cursor) Pagination Solution ($O(1)$)
Keyset pagination utilizes an indexed column as a seek cursor, allowing the database B-Tree index to jump instantly to the exact target node without reading discarded rows.

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ✅ O(1) Keyset Cursor Pagination Query
    @Query("""
        SELECT o FROM Order o 
        WHERE (o.createdAt < :lastCreatedAt OR (o.createdAt = :lastCreatedAt AND o.id < :lastId))
        ORDER BY o.createdAt DESC, o.id DESC
        """)
    List<Order> findNextOrderPage(
        @Param("lastCreatedAt") Instant lastCreatedAt,
        @Param("lastId") Long lastId,
        Pageable pageable // Used strictly for LIMIT sizing
    );
}
```

---

## 3. Persistence Verification Checklist

```text
[ ] 1. PK Generation Audit: Verify high-volume insert entities utilize `SEQUENCE` generators with allocation size > 1.
[ ] 2. Batch Verification : Ensure JDBC batching properties (`jdbc.batch_size`) are actively configured.
[ ] 3. Paging Optimization: Replace `PageRequest.of(page, size)` offset queries with keyset cursor parameters.
```
