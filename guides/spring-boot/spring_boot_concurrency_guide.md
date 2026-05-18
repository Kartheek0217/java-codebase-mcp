# Spring Boot Concurrency & Virtual Threads Guide

This guide establishes standards for concurrent execution in Spring Boot 3+: leveraging Project Loom virtual threads for massive concurrency, preventing thread pinning traps, and configuring asynchronous WebClient architectures.

---

## 1. Virtual Threads Architecture (Project Loom)

In traditional Java server architectures, platform threads map 1:1 directly to OS kernel threads. Because OS threads allocate $1\text{MB}$ of stack memory upfront and require expensive CPU kernel context switches, allocating $>10,000$ concurrent threads instantly crashes the JVM with `OutOfMemoryError: unable to create new native thread`.

```text
+---------------------------------------------------------------------------------------+
|                    Platform Threads vs. Virtual Threads Memory Model                  |
+---------------------------------------------------------------------------------------+
| Platform Threads (1:1 OS Kernel Mapping):                                             |
| [ 10,000 Platform Threads ] ---> Allocates 10GB RAM ---> Exhausts OS ulimit & Memory! |
|                                                                                       |
| Virtual Threads (M:N Carrier Thread Multiplexing):                                    |
| [ 1,000,000 Virtual Threads ] ---> Managed in JVM Heap (A few KB each) ---> 0 OS Exhaustion!|
+---------------------------------------------------------------------------------------+
```

### Enabling Virtual Threads in Spring Boot 3.2+
Enabling virtual threads automatically replaces the standard Tomcat web container platform thread pool with virtual threads.
```properties
spring.threads.virtual.enabled=true
```

---

## 2. The Carrier Thread Pinning Trap

When a virtual thread executes a blocking I/O operation (e.g., waiting for database responses or HTTP payloads), the JVM automatically unmounts the virtual thread from its underlying OS carrier thread, allowing the carrier thread to execute other virtual threads.

```text
+---------------------------------------------------------------------------------------+
|                            The Carrier Pinning Catastrophe                            |
+---------------------------------------------------------------------------------------+
| Virtual Thread executing blocking DB call inside a `synchronized` block:              |
| [ Virtual Thread ] ---> Enters synchronized method ---> JVM CANNOT unmount!           |
|                                                                                       |
| Carrier OS thread is pinned ---> 10 concurrent synchronized DB calls pin all carriers |
| ---> Complete JVM thread pool starvation!                                             |
+---------------------------------------------------------------------------------------+
```

### The Invariant: Replace `synchronized` with `ReentrantLock`
To prevent thread pinning, audit your codebase and third-party drivers to ensure blocking operations never execute inside `synchronized` blocks or methods. Always replace `synchronized` with `java.util.concurrent.locks.ReentrantLock`.

```java
// ❌ Thread Pinning Trap
public synchronized void executeDatabaseOperation() {
    repository.saveBlocking(entity); // PINS CARRIER THREAD!
}

// ✅ Carrier Unmount Safe Pattern
private final Lock lock = new ReentrantLock();

public void executeDatabaseOperation() {
    lock.lock();
    try {
        repository.saveBlocking(entity); // Safely unmounts virtual thread!
    } finally {
        lock.unlock();
    }
}
```

---

## 3. High-Performance Asynchronous `WebClient`

When executing remote HTTP calls across downstream microservices, replace legacy synchronous `RestTemplate` with non-blocking, reactive `WebClient`.

```java
@Configuration
public class WebClientConfiguration {

    @Bean
    public WebClient domainWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("domain-pool")
            .maxConnections(500)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofMillis(5000))
            .build();

        HttpClient httpClient = HttpClient.create(provider)
            .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl("https://api.internal.enterprise.com")
            .build();
    }
}
```

---

## 4. Concurrency Verification Checklist

```text
[ ] 1. Virtual Activation: Verify `spring.threads.virtual.enabled=true` is set across Spring Boot 3.2+ environments.
[ ] 2. Pinning Audit     : Scan codebase to eliminate `synchronized` blocks wrapping blocking I/O operations.
[ ] 3. WebClient Pooling : Ensure all reactive `WebClient` beans configure explicit connection pool constraints.
```
