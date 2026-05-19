# JCB Skills Registry

This directory contains global built-in skills for the JCB MCP server, segregated by project type.

## Directory Structure

- **[global](./global/)**: General-purpose jcb modes, reviews, commits, testing, and javadoc generation.
  - `jcb/`: Core JCB terse communication mode.
  - `jcb-commit/`: Ultra-compressed git commit message generator.
  - `jcb-javadoc/`: Strict template method-level Javadoc generator.
  - `jcb-junit/`: Spring Service JUnit test generator with full branch coverage.
  - `jcb-review/`: Java/Spring Boot code quality, concurrency, and security reviewer.
- **[java](./java/)**: Pure Java language-level architecture and style guides.
  - `java-clean-code/`: Naming, syntax, and exception handling guidelines.
  - `java-collections/`: Java Collections Framework usage and performance guide.
  - `java-memory-gc/`: Memory management, leak prevention, and JVM GC optimization.
  - `java-performance/`: Loop optimization, object reuse, and concurrency practices.
  - `java-streams/`: Functional programming style and Stream API best practices.
- **[spring-boot](./spring-boot/)**: Spring Boot framework-specific rules.
  - `spring-boot-architecture/`: Multi-layered controller-service-repository patterns and events.
  - `spring-boot-concurrency/`: `@Async`, virtual threads, scheduler, and lock management.
  - `spring-boot-jpa/`: Hibernate/JPA fetch strategies, transactions, and caching.
  - `spring-boot-performance/`: Boot time optimization, connection pooling, and JVM tuning.
  - `spring-boot-security/`: Authentication, authorization, cors, and secure headers.
  - `spring-boot-utilities/`: Jackson, logging, caching, and validation helpers.
- **[sql](./sql/)**: SQL database access and query optimizations.
  - `sql-jooq/`: Safe DSL building, code generation, and query building guide.
  - `sql-performance/`: Indexing, query tuning, pagination, and bulk operations.
- **[typescript](./typescript/)**: Frontend/Node/TypeScript rules.
  - `ts-architecture/`: Directory layout, type safety, naming, and dependency patterns.

## Auto-Scanning

All skills are recursively scanned and registered at system startup from the `classpath*:skills/jcb/**/SKILL.md` pattern.
