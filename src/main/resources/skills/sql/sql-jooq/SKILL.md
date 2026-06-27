---
name: sql-jooq
description: Safe DSL building, code generation, and query building.
---

# SQL jOOQ Guide

## 1. Code Generation
- Ensure codegen is synchronized with DB migrations to guarantee type-safety.
- Keep table references static.

## 2. Query Construction & Predictability
- **Type-Safe Queries**: Use `DSLContext` for type-safe queries. Compile-time validation protects against column/table renames.
- **Explicit Ownership**: Do not fetch heavy JPA entities or rely on implicit relationships (avoid lazy loading triggers). Write flat, explicit `select()` statements of only required fields.
- **Zero Reflection Projection**: Project results directly into immutable records (DTOs) via `.fetchInto(Dto.class)` to eliminate dirty checking and hydration overhead.
- **Diagnostics**: Configure `org.jooq` to log exact SQL queries with inline bind values during troubleshooting.

## 3. Hybrid CQRS Architecture
- **Command Pathway (Writes)**: Enforce business logic, audits, validations, and optimistic locks via Hibernate/JPA entity graphs.
- **Query Pathway (Reads)**: Bypass Hibernate entirely. Direct read queries to jOOQ for predictable plans, index-scans, and low memory usage.

## 4. Verification Checklist
- [ ] No raw string concatenations in jOOQ queries (SQL Injection prevention).
- [ ] Table/Field references match generated metadata.
- [ ] Hibernate models are excluded from read-only query repository flows.
- [ ] Read-only repository queries project fields explicitly via `.fetchInto()` records.
- [ ] Bind value log formatting (`org.jooq` level) is verified for debug configurations.
