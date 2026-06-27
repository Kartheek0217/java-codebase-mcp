# Enterprise Architecture & Engineering Guides

This repository contains definitive guides establishing production-grade engineering invariants, architectural standards, and high-performance patterns across Java, Spring Boot, Relational SQL, and TypeScript domains.

```text
+---------------------------------------------------------------------------------------+
|                            Enterprise Domain Guides Directory                         |
+---------------------------------------------------------------------------------------+
| ☕ Java Domain (`guides/java/`):                                                      |
|   ├── clean_code_guide.md       (Polymorphic branching, guard clauses, rule engines)  |
|   ├── colletions_guide.md       (Algorithmic complexity, dynamic pre-sizing, concurrent)|
|   ├── streams_guide.md          (Hot-path benchmarks, custom collectors, single-pass) |
|   ├── memory_and_gc_guide.md    (Memory leak identification, OOM prevention, G1GC)    |
|   └── performance_guide.md      (Profiling, 13 secrets, N+1 query elimination)        |
|                                                                                       |
| 🍃 Spring Boot Domain (`guides/spring-boot/`):                                        |
|   ├── spring_boot_architecture_guide.md (Decoupled events, defense layer pipelines)   |
|   ├── spring_boot_utilities_guide.md    (Senior utility design, 62 Spring utilities)  |
|   ├── spring_boot_performance_guide.md  (1M+ RPS tuning, AOT compilation, preheating) |
|   ├── spring_boot_jpa_guide.md          (PK batching, O(1) keyset cursor pagination)  |
|   ├── spring_boot_security_guide.md     (Transparent field encryption, blind indexing)|
|   └── spring_boot_concurrency_guide.md  (Virtual threads, pinning traps, WebClient)   |
|                                                                                       |
| 🗄️ Relational SQL Domain (`guides/sql/`):                                             |
|   ├── sql_guide.md              (10 query optimizations, sargability, composite rules)|
|   └── jooq_guide.md             (Compile-time safe DSL, direct DTOs, hybrid CQRS)     |
|                                                                                       |
| 📜 TypeScript Domain (`guides/typescript/`):                                          |
|   └── typescript_guide.md       (Type vs Interface contracts, Immutability invariants)|
+---------------------------------------------------------------------------------------+
```

---

## ☕ Java Architecture Guides

### 1. [Clean Code & Branching Architecture](java/clean_code_guide.md)
- **Core Focus:** Eliminating deeply nested `if-else` branching, functional command dispatch maps, modular rule engines, and Enum singleton equality.
- **Key Takeaways:** Invert validations using early return guard clauses; evaluate Enum singleton equality strictly via `==`; transition legacy switches to modern exhaustive switch expressions.

### 2. [Collections Architecture & Optimization](java/colletions_guide.md)
- **Core Focus:** Algorithmic complexity, pre-sizing Map and List allocations, and lock-free concurrent data structures (`ConcurrentHashMap`).
- **Key Takeaways:** Eliminate `LinkedList`, `Vector`, and `Hashtable`; compute explicit initial capacity (`N / 0.75`); utilize concurrent collections for multi-threaded safety.

### 3. [Streams Architecture & Optimization](java/streams_guide.md)
- **Core Focus:** Hot-path loop benchmarks, single-pass reductions, custom Stream collectors, and parallel stream blocking I/O isolation.
- **Key Takeaways:** Replace streams with primitive indexed loops in ultra-low latency SLAs; condense multi-pass filters into single custom collectors; never execute blocking I/O inside `parallelStream()`.

### 4. [Memory Architecture & Garbage Collection](java/memory_and_gc_guide.md)
- **Core Focus:** Unbounded cache leak prevention, `ThreadLocal` pool lifecycle management, `OutOfMemoryError` crash diagnostics, and G1GC generational tuning.
- **Key Takeaways:** Use bounded caches (`Caffeine`); clear thread-locals via `try-finally`; enforce identical `-Xms` and `-Xmx` heap bounds.

### 5. [Performance Architecture & Profiling](java/performance_guide.md)
- **Core Focus:** Production profiling, the 13 performance secrets of senior engineers, $N+1$ database query elimination, bulkhead thread isolation, and local caching.
- **Key Takeaways:** Profile live environments before refactoring; replace string concatenation in loops with `StringBuilder`; bulk fetch relationships in $O(n)$ queries.

---

## 🍃 Spring Boot Architecture Guides

### 6. [Spring Boot Enterprise Architecture](spring-boot/spring_boot_architecture_guide.md)
- **Core Focus:** Decoupled event publishing (`@EventListener`, `@TransactionalEventListener`) and two-tier request defense pipelines (Filters vs. Interceptors).
- **Key Takeaways:** Guarantee transaction commit boundaries via `@TransactionalEventListener`; position CORS/Security logic in Filters and Controller metadata validations in Interceptors.

### 7. [Spring Boot Utilities & Helper Architecture](spring-boot/spring_boot_utilities_guide.md)
- **Core Focus:** Senior utility class design invariants and cataloging the 62 built-in Spring Framework utilities.
- **Key Takeaways:** Enforce `final` classes with private constructors throwing `AssertionError`; utilize Spring's built-in helper suite (`StringUtils`, `StreamUtils`, `UriComponentsBuilder`) before adding third-party packages.

### 8. [Spring Boot Performance & High Throughput](spring-boot/spring_boot_performance_guide.md)
- **Core Focus:** Connection pool sizing for 1M+ RPS throughput (`HikariCP`, `Tomcat`), JVM cold start reduction via Spring AOT, and proactive cache preheating.
- **Key Takeaways:** Tune HikariCP via Little's Law; compile containerized services using Spring AOT; prevent cache stampedes via `ApplicationReadyEvent` preheaters.

### 9. [Spring Boot JPA & Persistence Architecture](spring-boot/spring_boot_jpa_guide.md)
- **Core Focus:** Primary key generation strategies for preserving JDBC batching (`SEQUENCE` vs `IDENTITY`) and constant-time $O(1)$ keyset cursor pagination.
- **Key Takeaways:** Avoid `IDENTITY` ID generation to enable JDBC multi-insert batching; replace offset paging (`PageRequest`) with indexed cursor parameters.

### 10. [Spring Boot Security & Data Protection](spring-boot/spring_boot_security_guide.md)
- **Core Focus:** Transparent JPA field-level encryption (`@Converter`) and deterministic blind indexing for high-speed ciphertext searching.
- **Key Takeaways:** Protect sensitive PII at the entity boundary using `AttributeConverter`; compute salted SHA hashes (blind index) to enable exact-match SQL queries over encrypted columns.

### 11. [Spring Boot Concurrency & Virtual Threads](spring-boot/spring_boot_concurrency_guide.md)
- **Core Focus:** Project Loom virtual threads (`spring.threads.virtual.enabled`), carrier thread pinning prevention, and high-performance reactive `WebClient` pooling.
- **Key Takeaways:** Replace `synchronized` blocks with `ReentrantLock` to prevent virtual thread carrier pinning; configure explicit connection timeouts on downstream WebClient pools.

---

## 🗄️ Relational SQL Domain Guides

### 12. [Enterprise SQL Architecture & Optimization](sql/sql_guide.md)
- **Core Focus:** 10 structural SQL query optimizations, eliminating leading wildcards, avoiding index-blinding functions (sargability), and leftmost prefix composite index design.
- **Key Takeaways:** Never wrap indexed columns in functions; replace compound `OR` with `UNION ALL`; project minimal columns to enable covering index matches.

### 13. [jOOQ & Compile-Time Safe Persistence](sql/jooq_guide.md)
- **Core Focus:** Compile-time verified database querying (jOOQ DSL), high-speed direct DTO record projections, and hybrid CQRS persistence architectures.
- **Key Takeaways:** Regenerate jOOQ classes automatically upon Flyway migrations; project query results directly into immutable records; isolate complex write entities in Hibernate and high-speed reads in jOOQ.

---

## 📜 TypeScript Domain Guides

### 14. [Enterprise TypeScript Architecture](typescript/typescript_guide.md)
- **Core Focus:** Structural contract evaluation (`type` vs `interface`), extensibility composition, and enforcing declaration immutability invariants.
- **Key Takeaways:** Default strictly to `type` aliases for all standard application models to guarantee declaration immutability and prevent unintended silent contract collisions.
