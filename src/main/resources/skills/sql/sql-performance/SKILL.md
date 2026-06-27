---
name: sql-performance
description: Indexing, query tuning, pagination, and bulk operations.
---

# SQL Performance

## 1. Indexing
- Create indexes on frequently searched/joined columns.
- Avoid indexing columns with low cardinality.

## 2. Pagination
- Avoid `OFFSET` pagination for large tables; use keyset/seek pagination.
  ```sql
  SELECT * FROM orders WHERE id > :last_id ORDER BY id LIMIT 50;
  ```

## 3. Verification Checklist
- [ ] Indexes exist for all foreign keys.
- [ ] Heavy queries analyzed via `EXPLAIN`.
