# Java Collections Architecture & Optimization Guide

This guide establishes strict performance invariants and architectural patterns for selecting, sizing, and operating high-performance Java collection structures across enterprise workloads.

---

## 1. Algorithmic Complexity & Collection Selection

Selecting the incorrect collection structure instantly introduces severe scalability bottlenecks ($O(n)$ vs. $O(1)$ lookups) under heavy load.

```text
+-----------------------------------------------------------------------------------------------+
|                            Java Collections Algorithmic Complexity                            |
+----------------------+--------------------+--------------------+--------------------+---------+
| Collection Structure | Index Access / Get | Search / Contains  | Insertion (Add)    | Deletion|
+----------------------+--------------------+--------------------+--------------------+---------+
| ArrayList            | O(1)               | O(n)               | O(1) amortized     | O(n)    |
| LinkedList           | O(n)               | O(n)               | O(1)               | O(1)    |
| ArrayDeque           | O(1) head/tail     | O(n)               | O(1)               | O(1)    |
| HashMap / HashSet    | O(1) average       | O(1) average       | O(1) average       | O(1)    |
| TreeMap / TreeSet    | O(log n)           | O(log n)           | O(log n)           | O(log n)|
+----------------------+--------------------+--------------------+--------------------+---------+
```

### Core Invariants
1. **Never use `LinkedList`:** Despite textbook $O(1)$ insertion claims, `LinkedList` exhibits atrocious CPU cache locality due to scattered heap node allocations. `ArrayList` or `ArrayDeque` consistently outperform `LinkedList` across virtually all production benchmarks.
2. **Eliminate Legacy Synchronized Containers:** Never use legacy `Vector` or `Hashtable`. They utilize coarse method-level locks that severely degrade throughput.

---

## 2. Dynamic Array Sizing & Rehashing Mechanics

### HashMap Pre-Sizing Formula
When populating a `HashMap` or `HashSet` with a known target element count ($N$), omitting the initial capacity forces the JVM to execute continuous array re-allocations and expensive rehashing operations.

```text
+---------------------------------------------------------------------------------------+
|                              HashMap Rehashing Mechanics                              |
+---------------------------------------------------------------------------------------+
| Unsized HashMap (Default 16, Load Factor 0.75):                                       |
| Inserting 10,000 items ---> Resizes at 12, 24, 48... ---> 10 Expensive Array Rehashes!|
|                                                                                       |
| Pre-Sized HashMap formula: Capacity = Target_Elements / 0.75 + 1                      |
| preSizedMap = new HashMap<>(13_334) ---> Zero Array Allocations / Rehashes!           |
+---------------------------------------------------------------------------------------+
```

```java
// ✅ Optimal Initialization for 10,000 elements
int expectedElements = 10_000;
int capacity = (int) Math.ceil(expectedElements / 0.75);
Map<String, User> userCache = new HashMap<>(capacity);
```

### ArrayList Pre-Sizing
Similarly, always initialize `ArrayList` with expected bounds to avoid $1.5\times$ backing array copies.
```java
List<OrderDto> dtos = new ArrayList<>(entities.size());
```

---

## 3. High-Concurrency Collection Architecture

When operating across highly concurrent multi-threaded web servers, standard collections fail with `ConcurrentModificationException` or silent data corruption.

```text
+-----------------------------------+---------------------------------------------------+
| Concurrent Collection             | Production Use Case & Performance Characteristics |
+-----------------------------------+---------------------------------------------------+
| ConcurrentHashMap                 | Lock-free read access, striped bucket-level locks.|
| CopyOnWriteArrayList              | Read-heavy lists (listeners) with infrequent edits|
| ConcurrentSkipListMap             | Concurrent sorted key-value mapping (NavigableMap)|
| ArrayBlockingQueue                | Bounded thread inter-communication producer/consumer|
+-----------------------------------+---------------------------------------------------+
```

### Lock-Free Concurrency (`ConcurrentHashMap`)
Instead of locking the entire table, `ConcurrentHashMap` utilizes compare-and-swap (CAS) operations and fine-grained bucket locking (`synchronized` on individual table bin heads).
```java
private final Map<String, AtomicInteger> rateLimiters = new ConcurrentHashMap<>(256);

public void incrementRequestCount(String apiKey) {
    rateLimiters.computeIfAbsent(apiKey, k -> new AtomicInteger(0)).incrementAndGet();
}
```

---

## 4. Verification Invariants

```text
[ ] 1. Collection Audit   : Verify codebases eliminate all occurrences of `LinkedList`, `Vector`, and `Hashtable`.
[ ] 2. Pre-Sizing Formula : Ensure all known-size Map and List allocations compute explicit initial capacities.
[ ] 3. Concurrency Safety : Audit shared mutable static collections to guarantee thread-safe concurrent variants.
```
