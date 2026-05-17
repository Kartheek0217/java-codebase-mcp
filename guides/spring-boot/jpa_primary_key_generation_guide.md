# JPA & Hibernate Primary Key Generation Architecture Guide (`@GeneratedValue`)

In enterprise persistence architectures powered by JPA and Hibernate, selecting the correct primary key generation strategy is not merely a syntactic choice—it is a critical architectural decision that directly dictates database I/O performance, table locking mechanics, and JDBC batch insertion capabilities.

JPA defines four distinct primary key generation strategies via the `@GeneratedValue` annotation: `AUTO`, `IDENTITY`, `SEQUENCE`, and `TABLE`.

```text
+---------------------------------------------------------------------------------------+
|                 Architectural I/O Comparison: IDENTITY vs. SEQUENCE                   |
+---------------------------------------------------------------------------------------+
| ❌ GenerationType.IDENTITY (Disables JDBC Batching!):                                  |
| [ Hibernate Session ] ---> execute INSERT #1 ---> DB generates ID #1 ---> returns ID  |
|                       ---> execute INSERT #2 ---> DB generates ID #2 ---> returns ID  |
|                       (Every single entity insertion requires a full network round-trip) |
|                                                                                       |
| ✅ GenerationType.SEQUENCE (Enables High-Performance JDBC Batching):                  |
| [ Hibernate Session ] ---> fetch next 50 IDs from Sequence in 1 DB call               |
|                       ---> execute BATCH INSERT (50 records in 1 network round-trip!)  |
+---------------------------------------------------------------------------------------+
```

---

## Architectural Comparison Matrix

```text
+-----------------------+------------------------+------------------------+------------------------+------------------------+
| Architectural Vector  | GenerationType.AUTO    | GenerationType.IDENTITY| GenerationType.SEQUENCE| GenerationType.TABLE   |
+-----------------------+------------------------+------------------------+------------------------+------------------------+
| Underlying DB Object  | Determined by Dialect  | DB `AUTO_INCREMENT`    | DB `SEQUENCE` Object   | Physical Tracker Table |
| ID Allocation Timing  | Dialect Dependent      | Post-Insert (DB level) | Pre-Insert (Fetched)   | Pre-Insert (Row Lock)  |
| JDBC Batching Support | Dialect Dependent      | ❌ Strictly Disabled   | ✅ Fully Supported     | ✅ Fully Supported     |
| DB Network Round-Trips| Dialect Dependent      | $1$ per entity insert  | $1$ per sequence block | Multiple queries + lock|
| Primary Database Fit  | Multi-Tenant / Portable| MySQL / MariaDB        | PostgreSQL / Oracle    | Legacy / Non-Standard  |
+-----------------------+------------------------+------------------------+------------------------+------------------------+
```

---

## 1. `GenerationType.AUTO` (The Portable Default)

### 📌 Architectural Overview
`AUTO` is the default generation strategy when `@GeneratedValue` is specified without arguments. It delegates the physical generation mechanism entirely to the persistence provider (Hibernate) based on the configured SQL dialect.
- In **PostgreSQL** or **Oracle**, Hibernate automatically defaults to `SEQUENCE`.
- In **MySQL**, Hibernate defaults to `IDENTITY` (or table sequences in legacy Hibernate versions).

### 💻 Entity Implementation
```java
package com.example.demo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;
    private String sku;

    // Constructors, Getters, and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
```

### 🏢 Enterprise Use Case
`AUTO` is perfectly suited for multi-tenant SaaS platforms or packaged enterprise software where the target database management system (DBMS) is decoupled from the codebase. It allows the exact same entity definitions to deploy seamlessly whether a client runs on PostgreSQL, Oracle, or MySQL.

---

## 2. `GenerationType.IDENTITY` (Auto-Increment Persistence)

### 📌 Architectural Overview
`IDENTITY` relies directly on the database engine's native auto-increment capability (`AUTO_INCREMENT` in MySQL, `SERIAL` / `BIGSERIAL` in PostgreSQL). The primary key value is generated dynamically by the database engine at the exact moment the row is written to disk.

```text
[ session.persist(customer) ] ---> Entity added to Persistence Context (ID is NULL)
                              ---> Immediate flush() forced to execute SQL INSERT
                              ---> DB generates ID & returns it to Hibernate
```

### 💻 Entity Implementation
```java
package com.example.demo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String fullName;

    public Long getId() { return id; }
}
```

### 🚨 The Critical Batching Limitation Trap
Because the database generates the primary key only *after* the `INSERT` statement executes, Hibernate cannot assign an ID when an entity is initially passed to `entityManager.persist()`. 

To maintain transactional integrity within its first-level persistence context cache, Hibernate is forced to immediately flush the `INSERT` statement to the database. **This completely disables JDBC batching (`hibernate.jdbc.batch_size`), forcing individual network round-trips for every single entity.** Never use `IDENTITY` in batch processing or high-volume transactional pipelines.

---

## 3. `GenerationType.SEQUENCE` (The Enterprise Standard)

### 📌 Architectural Overview
`SEQUENCE` leverages dedicated database sequence objects (`CREATE SEQUENCE`). Before executing entity insertions, Hibernate requests the next sequence value from the database. Because Hibernate knows the entity ID before executing the table insert, it can queue entities in its action queue and execute massive, grouped JDBC batch insert statements.

### 💻 Entity Implementation & Allocation Tuning
```java
package com.example.demo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq_gen")
    // allocationSize = 50 tells Hibernate to reserve a block of 50 IDs in a single database call!
    @SequenceGenerator(
        name = "order_seq_gen", 
        sequenceName = "seq_customer_order_id", 
        allocationSize = 50
    )
    private Long id;

    private Double totalAmount;
    private String orderStatus;

    public Long getId() { return id; }
}
```

### 🔬 The `allocationSize` Performance Invariant
By default, `@SequenceGenerator` configures `allocationSize = 50` (using Hibernate's `pooled` or `pooled-lo` optimizer).
1. When the first entity is persisted, Hibernate executes `SELECT nextval('seq_customer_order_id')`. Assume it returns `1`.
2. Hibernate knows it now owns IDs `1` through `50` in its local JVM memory space.
3. As the application persists the next 49 `Order` entities, Hibernate assigns IDs instantly from JVM memory **with zero database round-trips**.
4. When entity #51 is persisted, Hibernate makes its second sequence call to grab the next block of 50 IDs.

### 🏢 Enterprise Use Case
`SEQUENCE` is the mandatory architecture for high-throughput enterprise systems (banking ledgers, e-commerce checkouts) operating on PostgreSQL or Oracle. It enables sub-millisecond ID assignment and maximum network batching throughput.

---

## 4. `GenerationType.TABLE` (The Legacy Simulator)

### 📌 Architectural Overview
`TABLE` simulates database sequence behavior by creating a separate physical database table (e.g., `id_generator`) that tracks current primary key counters for multiple entities. To assign an ID, Hibernate executes a pessimistic row lock (`SELECT ... FOR UPDATE`), increments the counter value, and updates the table.

```sql
-- Structure of the underlying tracking table
CREATE TABLE id_generator (
    gen_name VARCHAR(255) PRIMARY KEY,
    gen_val BIGINT NOT NULL
);
```

### 💻 Entity Implementation
```java
package com.example.demo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.TableGenerator;

@Entity
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "invoice_table_gen")
    @TableGenerator(
        name = "invoice_table_gen",
        table = "id_generator",
        pkColumnName = "gen_name",
        valueColumnName = "gen_val",
        pkColumnValue = "invoice_pk",
        allocationSize = 50
    )
    private Long id;

    private String invoiceNumber;

    public Long getId() { return id; }
}
```

### 🚨 Architectural Drawbacks
`TABLE` generation introduces extreme performance bottlenecks. Because every ID allocation requires a row-level database lock across a shared table, concurrent transactions queue up waiting for lock releases. Use `TABLE` generation exclusively when working with legacy or esoteric database engines that lack native sequence or auto-increment support.

---

## 5. Architectural Decision Flowchart

```text
                  [ Does the target DB support SEQUENCES? (e.g., PostgreSQL / Oracle) ]
                                      /                           \
                                   YES                             NO (e.g., MySQL / MariaDB)
                                   /                                 \
                     [ Use GenerationType.SEQUENCE ]           [ Use GenerationType.IDENTITY ]
                     (Configure allocationSize=50)             (Accept no JDBC Batching)
```

---

## 6. Enterprise Production Verification Checklist

```text
[ ] 1. JDBC Batch Auditing  : Ensure spring.jpa.properties.hibernate.jdbc.batch_size=50 in config.
[ ] 2. Sequence Optimization: Verify all @SequenceGenerator annotations explicitly set allocationSize.
[ ] 3. Identity Restriction : Verify GenerationType.IDENTITY is never used in batch processing workers.
[ ] 4. Sequence DDL Parity  : Ensure DB sequence DDL matches Hibernate allocation step sizes exactly.
```
