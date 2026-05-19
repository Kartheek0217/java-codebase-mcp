---
name: spring-boot-concurrency
description: Thread-safe Spring Boot services, asynchronous execution, scheduler patterns, and locking strategies.
---

# Spring Boot Concurrency

## 1. Service Thread Safety
- **State Invariant**: Spring beans are singletons. Never store request-specific state in fields.
- **Shared Maps**: Always use `ConcurrentHashMap` for shared mutable maps. Synchronize iterations.
  ```java
  synchronized (sessionStore) {
      sessionStore.entrySet().removeIf(e -> e.getValue().isExpired());
  }
  ```

## 2. Async Execution (`@Async`)
- **Custom Executors**: Always specify a named thread pool; never rely on default SimpleAsyncTaskExecutor.
  ```java
  @Async("taskExecutor")
  public CompletableFuture<Result> runTask() { ... }
  ```
- **Virtual Threads**: Enable virtual threads on Java 21+ for blocking operations (`spring.threads.virtual.enabled=true`).

## 3. Scheduled Tasks & Locking
- **Distributed Locking**: Use `ShedLock` or `Redisson` for `@Scheduled` tasks to prevent multiple microservice instances from running the task concurrently.
  ```java
  @Scheduled(cron = "0 0 * * * *")
  @SchedulerLock(name = "cleanupTask", lockAtMostFor = "15m")
  public void cleanup() { ... }
  ```

## 4. Verification Checklist
- [ ] Singleton beans contain no request/mutable state in fields.
- [ ] All `@Async` calls use a custom thread pool.
- [ ] Scheduled tasks on replica services utilize distributed locks.
- [ ] Collections iterated in scheduler are thread-safe or synchronized.
