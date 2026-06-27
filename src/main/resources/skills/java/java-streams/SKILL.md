---
name: java-streams
description: High-performance stream operations, hot-path iterations, custom collectors, and isolating parallel streams from blocking I/O.
---

# Java Streams

## 1. Stream API vs. Traditional Loops
- **Standard Code**: Streams are preferred for readability.
- **CPU-Bound Hot Paths**: Use traditional loops over arrays. Streams introduce non-trivial lambda allocation and polymorphic dispatch overhead.

## 2. Advanced Reductions & Collectors
- **Single-Pass Aggregation**: Avoid running multiple stream pipelines over the same collection. Use complex downstream collectors (`mapping`, `counting`, `summarizingDouble`).
- **Partitioning vs. Grouping**: Use `partitioningBy` instead of `groupingBy` for binary split predicates. `partitioningBy` is faster and guarantees that both `true` and `false` keys exist in the resulting map.
- **Custom Batching**: Collect stream elements into fixed-size batches (e.g. `BatchCollector`) for chunked database operations and API calls.
- **toMap Safety**: Always supply a merge function (resolver) to `Collectors.toMap()` to prevent `IllegalStateException` on duplicate keys.
  ```java
  Map<String, User> map = users.stream()
      .collect(Collectors.toMap(User::getId, Function.identity(), (existing, replacement) -> existing));
  ```
- **Null Safety & Key Ordering**:
  - Filter out `null` keys prior to collecting to prevent `NullPointerException`.
  - Supply `LinkedHashMap::new` to `groupingBy` to preserve sorted stream ordering.

## 3. Parallel Streams Guardrails
- **Common ForkJoinPool Contention**: All `.parallelStream()` calls in the JVM share the common ForkJoinPool.
- **Rules**:
  - Never execute blocking I/O (database, REST calls) inside `.parallelStream()`.
  - Use parallel streams only for pure CPU-bound tasks on large collections (`>100k` elements).
  - Lambda operations must be stateless and free of side-effects.

## 4. Verification Checklist
- [ ] No parallel streams containing blocking calls.
- [ ] Multi-pass collections refactored to single-pass.
- [ ] Hot-path processing uses primitive arrays/loops.
- [ ] `toMap()` invocations have explicit duplicate-key merge functions.
- [ ] `groupingBy` calls have null-filtering guards on key fields.
- [ ] `groupingBy` uses `LinkedHashMap::new` if key order must be preserved.
