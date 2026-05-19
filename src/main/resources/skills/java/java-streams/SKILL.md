---
name: java-streams
description: High-performance stream operations, hot-path iterations, custom collectors, and isolating parallel streams from blocking I/O.
---

# Java Streams

## 1. Stream API vs. Traditional Loops
- **Standard Code**: Streams are preferred for readability.
- **CPU-Bound Hot Paths**: Use traditional loops over arrays. Streams introduce non-trivial lambda allocation and polymorphic dispatch overhead.

## 2. Advanced Reductions & Collectors
- **Single-Pass Aggregation**: Avoid running multiple stream pipelines over the same collection. Use complex collectors.
  ```java
  Map<Status, Double> revenue = orders.stream()
      .collect(Collectors.groupingBy(Order::getStatus, Collectors.summingDouble(Order::getTotalAmount)));
  ```
- **Custom Collectors**: Enforce single-pass reduction on custom structures.
  ```java
  public record Result(int ok, int err, List<String> logs) {
      public Result merge(Result o) { return new Result(ok + o.ok, err + o.err, concat(logs, o.logs)); }
  }
  ```

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
