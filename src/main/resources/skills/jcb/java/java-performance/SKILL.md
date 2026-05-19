---
name: java-performance
description: High-performance JVM profiling, sargability, string builders, avoiding N+1 queries, and concurrency bulkheads.
---

# Java Performance & Profiling

## 1. Core Optimization Principles
- **String Builders**: Use `StringBuilder(capacity)` inside loops; never use string concatenation (`+`).
- **Primitive Types**: Use primitives (`long`) in hot paths to bypass JVM unboxing overhead.
- **Loop Optimization**: Plain loops (`for`) out-benchmark streams/lambdas in critical paths.
- **Resource Management**: Use `try-with-resources` to close I/O streams and database connections.
- **JMH Benchmarking**: Always use JMH for microbenchmarks to warm up JIT.
- **Granular Synchronization**: Lock minimal critical sections instead of method-level synchronization.
- **Bounded Pools**: Never call `new Thread().start()`. Use bounded thread pools.

## 2. Data Access & N+1 Queries
- **Batch Query Pattern**: Replace N+1 queries with bulk fetches and in-memory assembly.
  ```java
  List<User> users = userRepository.findAllActiveUsers();
  List<Long> ids = users.stream().map(User::getId).toList();
  Map<Long, List<Order>> ordersMap = orderRepository.findByUserIds(ids)
      .stream().collect(Collectors.groupingBy(Order::getUserId));
  users.forEach(u -> u.setOrders(ordersMap.getOrDefault(u.getId(), List.of())));
  ```
- **Bypass JPA on Hot Paths**: Use `JdbcTemplate` or raw SQL mappings to records for high-traffic read operations.

## 3. Concurrency & Caching
- **Bulkhead Pattern**: Isolate slow blocking remote HTTP calls in separate, dedicated thread pools.
- **Local Cache**: Cache static configurations in JVM memory using `Caffeine`.
  ```java
  Cache<Long, Config> cache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(15)).maximumSize(5000).build();
  ```

## 4. Verification Checklist
- [ ] No string concatenation inside loops.
- [ ] N+1 queries replaced by batch fetching/in-memory grouping.
- [ ] JPA bypassed on high-traffic read paths with `JdbcTemplate`/records.
- [ ] Blocking HTTP operations isolated to dedicated executors.
