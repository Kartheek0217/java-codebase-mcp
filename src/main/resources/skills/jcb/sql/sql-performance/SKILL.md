---
name: sql-performance
description: >
  Structural relational database query optimization, covering indexes, composite key ordering, and sargability rules.
---

# Enterprise SQL Architecture & Optimization Guide

This guide establishes rigorous standards for relational database query performance: mastering structural SQL optimization, avoiding index invalidation traps, enforcing sargable WHERE predicates, and designing high-efficiency composite indexes.

---

## 1. Structural SQL Optimization Architecture

When enterprise web applications experience severe latency degradation under load, the root cause is rarely the runtime code or network bandwidth. Most performance bottlenecks trace directly to unindexed full table scans, in-memory sorts, implicit type conversions, and subquery materialization.

```text
+---------------------------------------------------------------------------------------+
|                         Execution Pipeline: Full Scan vs. Index Lookup                |
+---------------------------------------------------------------------------------------+
| ❌ Full Table Scan (Unindexed OR / Function / Implicit Cast / Leading Wildcard):      |
| [ Disk Blocks: 10,000,000 Rows ] ---> Reads Every Single Row ---> High I/O & CPU Spike|
|                                                                                       |
| ✅ Index B-Tree Lookup (Sargable WHERE / Covering Index / Leftmost Prefix):           |
| [ B-Tree Root ] ---> Log(N) Traversal ---> Fetches Exact Target Page ---> 0.1ms Speed |
+---------------------------------------------------------------------------------------+
```

### The 10 Invariants of Structural SQL Optimization

#### 1. Eliminate Leading Wildcards (`LIKE '%term'`)
Placing a wildcard at the beginning of a `LIKE` pattern instantly disables B-Tree index traversal, forcing the database engine to inspect every row in the table.
```sql
-- ❌ Full Table Scan (Cannot traverse B-Tree)
SELECT id FROM users WHERE email LIKE '%@gmail.com';

-- ✅ Index Range Scan (B-Tree traversal enabled)
SELECT id FROM users WHERE email LIKE 'john.doe%';
```

#### 2. Sargability Enforcement (Never apply functions to indexed columns)
SARGable (Search Argument Able) queries allow the optimizer to utilize indexes. Wrapping an indexed column in a function (`UPPER()`, `YEAR()`, `DATE()`, `COALESCE()`) blinds the optimizer, triggering a full scan.
```sql
-- ❌ Full Table Scan (Function blinds index)
SELECT id FROM orders WHERE DATE(created_at) = '2026-05-18';

-- ✅ Sargable Range Query (Index Range Scan)
SELECT id FROM orders WHERE created_at >= '2026-05-18 00:00:00' AND created_at < '2026-05-19 00:00:00';
```

#### 3. Replace Single-Column & Compound `OR` with `UNION ALL`
The optimizer frequently fails to utilize indexes when multiple conditions are linked via `OR`, especially across different columns. Splitting the query into separate index-backed queries combined via `UNION ALL` preserves index lookups.
```sql
-- ❌ Full Table Scan (OR disables separate index seeks)
SELECT id, status FROM orders WHERE status = 'PENDING' OR customer_id = 99281;

-- ✅ Dual Index Seek + Union All
SELECT id, status FROM orders WHERE status = 'PENDING'
UNION ALL
SELECT id, status FROM orders WHERE customer_id = 99281;
```

#### 4. Explicit Column Projection (Never use `SELECT *`)
```sql
-- ❌ Inflates Network I/O, RAM allocation, and defeats Covering Indexes
SELECT * FROM products WHERE category = 'ELECTRONICS';

-- ✅ Minimal Data Transfer + Potential Covering Index Match
SELECT id, name, price FROM products WHERE category = 'ELECTRONICS';
```

#### 5. Replace `NOT IN` with `NOT EXISTS`
When executing `NOT IN (SELECT ...)`, if the subquery returns even a single `NULL` value, the entire query returns zero rows due to SQL ternary logic (`col != NULL` evaluates to `UNKNOWN`). `NOT EXISTS` utilizes indexed correlated subquery lookups and correctly ignores nulls.

#### 6. Keyset Indexing over Offset Pagination
Avoid `OFFSET`. Use indexed cursor columns (`WHERE created_at < @last_timestamp`).

#### 7. Covering Indexes (Eliminate Table Lookups)
When a secondary B-Tree leaf node contains all columns required by the `SELECT` clause, the database satisfies the query directly from the index pages without hitting the primary clustered table rows.
```sql
CREATE INDEX idx_orders_status_price ON orders(status, total_amount);
-- Index Only Scan (0 Primary Table Lookups!)
SELECT total_amount FROM orders WHERE status = 'COMPLETED';
```

#### 8. Prevent Implicit Type Conversions
When querying a `VARCHAR` column using an unquoted numeric literal, the database engine casts every table column value to a number before comparing, triggering a full scan.
```sql
-- ❌ Implicit Cast Full Table Scan (account_number is VARCHAR)
SELECT id FROM accounts WHERE account_number = 99812;

-- ✅ Direct Index Seek
SELECT id FROM accounts WHERE account_number = '99812';
```

#### 9. Leftmost Prefix Invariant (Composite Indexing)
A composite index `(col_a, col_b, col_c)` can only satisfy queries that filter on `col_a`, `(col_a, col_b)`, or `(col_a, col_b, col_c)`. A query filtering strictly on `WHERE col_b = 'X'` cannot traverse the B-Tree hierarchy.

#### 10. Cardinality Index Ordering (`col_eq, col_sort, col_range`)
When designing composite indexes, order columns by equality filters first (high selectivity), sorting columns second, and range filters (`<, >`) last.

---

## 2. SQL Architecture Verification Checklist

```text
[ ] 1. Sargable Audit    : Verify all WHERE clauses avoid wrapping indexed columns inside SQL functions.
[ ] 2. Wildcard Audit    : Ensure zero query strings utilize leading wildcards (`%term`).
[ ] 3. Implicit Castings : Audit query parameter bindings to guarantee strict type alignment with DB schemas.
```
