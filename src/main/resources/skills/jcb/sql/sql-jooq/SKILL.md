---
name: sql-jooq
description: Safe DSL building, code generation, and query building.
---

# SQL jOOQ Guide

## 1. Code Generation
- Ensure codegen is synchronized with DB migrations to guarantee type-safety.
- Keep table references static.

## 2. Query Construction
- Use `DSLContext` for type-safe queries.
  ```java
  public List<Record> fetchActiveProjects(DSLContext dsl) {
      return dsl.selectFrom(PROJECT)
          .where(PROJECT.STATUS.eq("ACTIVE"))
          .fetch();
  }
  ```

## 3. Verification Checklist
- [ ] No raw string concatenations in jOOQ queries (SQL Injection prevention).
- [ ] Table/Field references match generated metadata.
