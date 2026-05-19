---
name: java-performance
description: >
  High-performance JVM profiling, sargability, string builders, avoiding N+1 queries, and concurrency bulkheads.
---

# Java Performance Architecture & Profiling Guide

This guide establishes battle-tested principles for production profiling, eliminating database bottlenecks, and optimizing execution throughput across high-traffic enterprise Java applications.

---

## 1. Profiling First & The 13 Performance Secrets

### The Core Principle
> If you do not profile before modifying code, you are not optimizing—you are gambling.

Before refactoring, collect empirical metrics to identify exact execution bottlenecks:
- **JFR (Java Flight Recorder) & VisualVM / JConsole:** Low-overhead production profiling for CPU, memory allocation, and thread lock contention.
- **Async-profiler / YourKit:** Deep thread and allocation profiling.
- **Gatling / JMeter:** Load testing to benchmark P99 latency SLAs.

### The 13 Performance Secrets of Senior Engineers

```text
+---------------------------------------------------------------------------------------+
|                      Senior Java Performance Optimization Toolkit                     |
+---------------------------------------------------------------------------------------+
|  1. String Allocation Control     |  6. Resource Management  | 10. Granular Locking   |
|  2. Collection Pre-Sizing         |  7. JIT Warm-Up & JMH    | 11. Bounded Pools      |
|  3. Primitive Unboxing Enforcement|  8. Lazy Slicing         | 12. Profiling First    |
|  4. Hot-Path Loop Fallback        |  9. Explicit GC Tuning   | 13. Object Pool Reuse  |
|  5. Memoization & Caching         |                          |                        |
+---------------------------------------------------------------------------------------+
```

1. **String Allocation Control:** Never use string concatenation (`+`) inside iterative loops. Always use explicit `StringBuilder(capacity)` to eliminate quadratic ($O(n^2)$) allocation thrash.
2. **Collection Pre-Sizing:** Explicitly pre-size collections (`new HashMap<>(10_000)`) to eliminate dynamic array re-allocations and rehashing.
3. **Primitive Enforcement:** Audit hot paths for silent autoboxing (`Long` vs `long`). Primitives reside directly on the thread stack, requiring zero heap allocation.
4. **Hot-Path Loop Fallback:** In CPU-bound hot paths, traditional `for` loops and array iteration out-benchmark functional streams due to lambda dispatch overhead.
5. **Memoization & Caching:** Never compute the same expensive database lookup, external REST call, or cryptographic hash twice. Cache static configurations in JVM memory.
6. **Explicit Resource Closing:** Always wrap I/O, database connections, and sockets in `try-with-resources`. Never rely on GC or finalizers.
7. **JIT Warm-Up (JMH):** The JVM JIT compiler requires warm-up iterations. Never benchmark with naive `System.currentTimeMillis()` timers; always use Java Microbenchmark Harness (JMH).
8. **Lazy Data Slicing:** Never load entire database tables into memory (`findAll()`). Enforce strict cursor pagination or `Slice<T>` chunking.
9. **GC Match:** Select the GC matching your exact architecture (G1GC for balanced heaps $>4\text{GB}$; ZGC / Shenandoah for ultra-low sub-millisecond pauses).
10. **Granular Synchronization:** Never use method-level `synchronized` keywords. Isolate shared write operations to minimal, fine-grained critical section locks.
11. **Bounded Thread Pools:** Never call `new Thread().start()`. Allocating raw OS threads exhausts memory and causes CPU context-switching thrash. Use bounded `ExecutorService` pools.
12. **Measure First:** Profile live environments to locate exact hotspots before writing optimization patches.
13. **Constant Literal Reuse:** Define immutable static constants (`public static final String KEY = "val";`) to maximize String Pool reuse and eliminate redundant heap allocations.

---

## 2. Data Access & $N+1$ Query Elimination

Production bottlenecks in REST APIs are overwhelmingly caused by $N+1$ database queries and unoptimized serialization.

### ❌ The $N+1$ Query Disaster
```java
// 1 Query for Users + 500 for Orders = 501 Queries!
List<User> users = userRepository.findAllActiveUsers();
for (User user : users) {
    List<Order> orders = orderRepository.findOrdersByUserId(user.getId());
}
```

### ✅ The Fix: Bulk Fetching & In-Memory Assembly ($O(n)$)
Execute exactly 2 bulk SQL queries using `IN (...)` clauses, then assemble relationships in memory using Java Streams and Maps.
```java
// Step 1: Bulk fetch users (1 query)
List<User> users = userRepository.findAllActiveUsers();
List<Long> userIds = users.stream().map(User::getId).toList();

// Step 2: Bulk fetch orders and index by userId in memory (1 query)
Map<Long, List<Order>> ordersMap = orderRepository.findOrdersByUserIds(userIds)
    .stream()
    .collect(Collectors.groupingBy(Order::getUserId));

// Assemble in memory
for (User user : users) {
    user.setOrders(ordersMap.getOrDefault(user.getId(), List.of()));
}
```

### Bypassing JPA on Hot Paths
Spring Data JPA and Hibernate provide rapid developer velocity but introduce proxy allocations and dirty-checking overhead on high-throughput read paths.
- **The Rule:** Keep JPA for complex entity mutations and CRUD, but use lightweight `JdbcTemplate` or raw SQL mapped to immutable records for high-traffic query endpoints.

```java
public List<UserDto> fetchActiveUsers() {
    return jdbcTemplate.query(
        "SELECT id, name, email FROM users WHERE active = true",
        (rs, rowNum) -> new UserDto(rs.getLong("id"), rs.getString("name"), rs.getString("email"))
    );
}
```

---

## 3. Concurrency, Caching & Thread Pools

### Isolating Blocking I/O (Bulkhead Pattern)
Executing blocking synchronous remote HTTP calls directly on Tomcat web worker threads causes downstream slowdowns to quickly exhaust the web server pool, crashing the service.
- **The Fix:** Isolate slow remote calls to a separate, dedicated `ExecutorService` thread pool.

```java
@Configuration
public class ThreadPoolConfig {
    @Bean
    public ExecutorService remoteCallExecutor() {
        return Executors.newFixedThreadPool(50);
    }
}
```

### High-Performance Local In-Memory Caching (`Caffeine`)
Even Redis distributed caching requires a TCP network round-trip. Highly static domain data (tenant configurations, access permissions, subscription tiers) should be cached locally in JVM memory using **Caffeine**.
```java
Cache<Long, TenantConfig> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(15))
    .maximumSize(5_000)
    .build();

public TenantConfig getConfig(Long tenantId) {
    return cache.get(tenantId, id -> repository.fetchConfig(id));
}
```

---

## 4. Real Production Impact Summary

```text
+------------------------------+---------------------------+---------------------------+
| Performance Metric           | Before Optimization       | After Refactoring         |
+------------------------------+---------------------------+---------------------------+
| Endpoint Latency (P99)       | 800ms – 1.5s              | 70ms – 120ms (10x faster) |
| DB Queries Per Request       | 200 – 500 queries         | 3 – 8 queries             |
| Average CPU Utilization      | 80% – 95%                 | 20% – 35%                 |
| System Stability Under Load  | Timeouts & 5xx HTTP spikes| 100% Stable               |
+------------------------------+---------------------------+---------------------------+
```

### Core Takeaways
1. **Java is not slow; unoptimized architecture is slow.**
2. If an application suffers from $N+1$ database queries, blocking synchronous I/O, and unoptimized memory allocation, an architectural data access refactor yields dramatically higher return on investment than superficial micro-optimizations.
