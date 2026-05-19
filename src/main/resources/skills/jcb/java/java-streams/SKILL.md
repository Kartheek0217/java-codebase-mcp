---
name: java-streams
description: >
  High-performance stream operations, hot-path iterations, custom collectors, and isolating parallel streams from blocking I/O.
---

# Java Streams Architecture & Optimization Guide

This guide establishes performance guidelines and advanced functional patterns for operating Java Streams: evaluating execution overhead in hot paths, designing custom collectors, and achieving single-pass multi-grouping reductions.

---

## 1. Stream API vs. Traditional Iteration (Hot-Path Benchmarks)

While the Java Stream API provides beautiful declarative ergonomics, it introduces non-trivial runtime overhead via lambda instantiation, intermediate object allocations, and polymorphic virtual method dispatching.

```text
+---------------------------------------------------------------------------------------+
|                      Hot-Path Iteration Benchmarking (JMH)                            |
+---------------------------------------------------------------------------------------+
| ❌ Functional Stream Pipeline (IntStream.range().filter().sum()):                     |
| Allocates pipeline objects ---> Virtual method dispatching ---> 15ms / 1M elements    |
|                                                                                       |
| ✅ Traditional Array Iteration (for (int i = 0; i < arr.length; i++)):               |
| CPU Cache Line Prefetching ---> JIT Loop Unrolling ---> 3ms / 1M elements (5x faster!)|
+---------------------------------------------------------------------------------------+
```

### Architectural Decision Rule
- **Standard Application & I/O Boundaries:** Use Streams. The overhead (nanoseconds) is completely negligible compared to database queries or network latency.
- **Ultra-Low Latency CPU-Bound Hot Paths:** In algorithmic trading engines, game loops, or data serialization pipelines executing millions of iterations per second, enforce traditional indexed `for` loops on primitive arrays.

---

## 2. Advanced Stream Reduction & Collectors

When transforming data, chaining multiple sequential `.filter().map().collect()` pipelines across the same underlying dataset causes redundant CPU iterations. Always condense operations into single-pass reductions using advanced collectors.

### 1. Grouping and Partitioning
```java
// Grouping orders by status and summing total amounts in a single pass
Map<OrderStatus, Double> revenueByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        Collectors.summingDouble(Order::getTotalAmount)
    ));

// Partitioning active vs. inactive users in one pass
Map<Boolean, List<User>> partitionedUsers = users.stream()
    .collect(Collectors.partitioningBy(User::isActive));
```

### 2. Custom Collectors for Complex Domain Reductions
When standard `Collectors` do not support complex domain aggregation logic, implement a custom `Collector`.

```java
public record BatchExecutionResult(int successful, int failed, List<String> errorLogs) {
    public BatchExecutionResult merge(BatchExecutionResult other) {
        List<String> mergedLogs = new ArrayList<>(this.errorLogs);
        mergedLogs.addAll(other.errorLogs);
        return new BatchExecutionResult(this.successful + other.successful, this.failed + other.failed, mergedLogs);
    }
}

// Custom Collector summarizing batch execution items in a single stream pass
public static Collector<BatchItem, ?, BatchExecutionResult> summarizingBatch() {
    return Collector.of(
        () -> new BatchExecutionResult(0, 0, new ArrayList<>()),
        (accumulator, item) -> {
            if (item.isSuccess()) {
                accumulator = new BatchExecutionResult(accumulator.successful() + 1, accumulator.failed(), accumulator.errorLogs());
            } else {
                accumulator.errorLogs().add(item.getErrorMessage());
                accumulator = new BatchExecutionResult(accumulator.successful(), accumulator.failed() + 1, accumulator.errorLogs());
            }
        },
        BatchExecutionResult::merge
    );
}
```

---

## 3. Parallel Streams Guardrails

Invoking `.parallelStream()` without analyzing the execution context is a frequent source of thread pool exhaustion and thread contention bugs.

```text
+---------------------------------------------------------------------------------------+
|                            ForkJoinPool Common Pool Contention                        |
+---------------------------------------------------------------------------------------+
| All .parallelStream() invocations across the entire JVM share the common ForkJoinPool |
| (Sized at Runtime.getRuntime().availableProcessors() - 1).                            |
|                                                                                       |
| Executing blocking I/O (HTTP calls, DB queries) inside a parallel stream instantly    |
| exhausts the shared pool, freezing all other parallel streams in the JVM!             |
+---------------------------------------------------------------------------------------+
```

### Core Invariants
1. **Never execute blocking I/O inside `.parallelStream()`:** Confine parallel streams strictly to pure in-memory CPU-bound calculations on massive datasets ($>100,000$ elements).
2. **Eliminate Stateful Lambdas:** Ensure operations passed to `.map()` or `.forEach()` are completely stateless and side-effect free.

---

## 4. Verification Checklist

```text
[ ] 1. Hot-Path Audit    : Replace Stream pipelines with primitive array loops in CPU-bound latency SLAs.
[ ] 2. Single-Pass Redux : Refactor multi-pass collection iterations into single-pass custom `Collectors`.
[ ] 3. Parallel Guarding : Audit all `.parallelStream()` usages to guarantee absolute isolation from blocking I/O.
```
