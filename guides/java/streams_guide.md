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

When transforming data, chaining multiple sequential `.filter().map().collect()` pipelines across the same underlying dataset causes redundant CPU iterations. Using the declarative `Collectors` utility class simplifies verbose loops into clean pipelines and enables multi-level reductions.

---

### 1. Grouping and Partitioning

#### GroupingBy & Downstream Mapping
The `groupingBy` collector is the primary tool for categorizing data. When grouping objects, you can use downstream collectors to transform the grouped elements.

```java
// Grouping employees by department
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDepartment));

// Downstream Mapping: Grouping only employee names by department
Map<String, List<String>> namesByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.mapping(Employee::getName, Collectors.toList())
    ));
```

#### PartitioningBy (Binary Split)
When splitting elements into exactly two groups based on a predicate, `partitioningBy` is significantly more efficient than using `groupingBy` with a boolean function.
* **Invariant**: `partitioningBy` guarantees that the resulting map contains entries for both `true` and `false` keys, even if one of the collections is empty. `groupingBy` only creates keys that exist in the stream elements.

```java
// Splitting employees by high salary condition
Map<Boolean, List<Employee>> splitBySalary = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.getSalary() > 100000));
```

---

### 2. Downstream Collectors for Complex Aggregations

Chaining downstream collectors allows multi-level grouping and statistical reductions inside a single stream pass.

#### Counting and Summarizing
```java
// Count employees per department
Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.counting()
    ));

// Get salary statistics (min, max, average, sum) per department
Map<String, DoubleSummaryStatistics> statsByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.summarizingDouble(Employee::getSalary)
    ));
```

#### Multi-level / Nested Grouping
```java
// Group by department, then subgroup by salary tier
Map<String, Map<String, List<Employee>>> nested = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.groupingBy(e -> {
            if (e.getSalary() < 50000) return "Junior";
            if (e.getSalary() < 100000) return "Mid";
            return "Senior";
        })
    ));
```

#### Reducing to Single Values
```java
// Find highest salary earner per department
Map<String, Optional<Employee>> highestPaid = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.maxBy(Comparator.comparing(Employee::getSalary))
    ));
```

---

### 3. Custom Collectors for Specialized Needs

When standard collectors are insufficient, you can implement custom structures using the `Collector` interface or factory methods.

#### Custom Batching Collector (Fixed-size batches)
Accumulating stream elements into fixed-size batches is highly useful for paginated API calls or batched database insertions.

```java
public class BatchCollector<T> implements Collector<T, List<List<T>>, List<List<T>>> {
    private final int batchSize;

    public BatchCollector(int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("Batch size must be > 0");
        this.batchSize = batchSize;
    }

    @Override
    public Supplier<List<List<T>>> supplier() {
        return ArrayList::new;
    }

    @Override
    public BiConsumer<List<List<T>>, T> accumulator() {
        return (batches, element) -> {
            if (batches.isEmpty() || batches.get(batches.size() - 1).size() >= batchSize) {
                batches.add(new ArrayList<>());
            }
            batches.get(batches.size() - 1).add(element);
        };
    }

    @Override
    public BinaryOperator<List<List<T>>> combiner() {
        return (left, right) -> {
            left.addAll(right);
            return left;
        };
    }

    @Override
    public Function<List<List<T>>, List<List<T>>> finisher() {
        return Function.identity();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.IDENTITY_FINISH);
    }
}

// Usage: Partitioning orders into chunks of 100
List<List<Order>> batches = orders.stream()
    .collect(new BatchCollector<>(100));
```

#### State Summary Collector (`Collector.of`)
For lighter domain-specific reductions, use `Collector.of` to build collectors inline:

```java
public record BatchExecutionResult(int successful, int failed, List<String> errorLogs) {
    public BatchExecutionResult merge(BatchExecutionResult other) {
        List<String> mergedLogs = new ArrayList<>(this.errorLogs);
        mergedLogs.addAll(other.errorLogs);
        return new BatchExecutionResult(this.successful + other.successful, this.failed + other.failed, mergedLogs);
    }
}

public static Collector<BatchItem, ?, BatchExecutionResult> summarizingBatch() {
    return Collector.of(
        () -> new BatchExecutionResult(0, 0, new ArrayList<>()),
        (acc, item) -> {
            if (item.isSuccess()) {
                acc = new BatchExecutionResult(acc.successful() + 1, acc.failed(), acc.errorLogs());
            } else {
                acc.errorLogs().add(item.getErrorMessage());
                acc = new BatchExecutionResult(acc.successful(), acc.failed() + 1, acc.errorLogs());
            }
        },
        BatchExecutionResult::merge
    );
}
```

---

### 4. Performance Considerations and Pitfalls

#### Duplicate Keys in `toMap()`
`Collectors.toMap()` throws `IllegalStateException` if the keys are not unique. Always supply a merge function for safety:
```java
Map<String, Employee> employeeMap = employees.stream()
    .collect(Collectors.toMap(
        Employee::getId,
        Function.identity(),
        (existing, replacement) -> existing // Keep the first key encountered
    ));
```

#### Order Guarantees
`groupingBy` does not guarantee the insertion order of keys. To enforce order (e.g., maintaining stream sorting order), supply a map constructor such as `LinkedHashMap::new`:
```java
Map<String, List<Employee>> ordered = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        LinkedHashMap::new,
        Collectors.toList()
    ));
```

#### Memory Footprint and OOM
Building massive collections (especially maps) in memory can exhaust JVM heap space. Consider processing data in chunk sizes, using pagination, or employing `groupingByConcurrent` with parallel streams for concurrent reductions.

#### Null Key Handling
Many collectors throw `NullPointerException` if keys evaluate to `null`. Always filter elements containing `null` key fields before grouping:
```java
Map<String, List<Employee>> safeMap = employees.stream()
    .filter(e -> e.getDepartment() != null)
    .collect(Collectors.groupingBy(Employee::getDepartment));
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
