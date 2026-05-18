# Spring Boot Performance & High Throughput Architecture Guide

This guide establishes production optimization patterns for Spring Boot microservices: tuning connection pools for 1M+ RPS throughput, minimizing JVM cold start latency via Spring AOT compilation, and executing proactive distributed cache preheating.

---

## 1. High Throughput Tuning (1M+ RPS Architecture)

When scaling Spring Boot services to handle millions of requests per second, standard out-of-the-box auto-configurations introduce thread pool exhaustion and TCP connection bottlenecking.

```text
+---------------------------------------------------------------------------------------+
|                         High-Throughput Pool Architecture                             |
+---------------------------------------------------------------------------------------+
| Tomcat Web Server Pool (Max 200 Threads)                                              |
|            |                                                                          |
|            v                                                                          |
| HikariCP Database Connection Pool (Sized via Little's Law: 2x Cores + Effective Spindles) |
+---------------------------------------------------------------------------------------+
```

### HikariCP Optimization Baseline
```properties
# Production HikariCP Baseline
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=2000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### Tomcat Web Container Tuning
```properties
server.tomcat.threads.max=400
server.tomcat.threads.min-spare=50
server.tomcat.max-connections=10000
server.tomcat.accept-count=1000
```

---

## 2. JVM Cold Start Reduction & Spring AOT

In containerized cloud environments (Kubernetes, AWS ECS), horizontal auto-scaling is frequently hindered by $30\text{s} - 60\text{s}$ Spring Boot JVM cold start times caused by classpath scanning and dynamic bytecode generation.

```text
+---------------------------------------------------------------------------------------+
|                      JIT Classpath Scanning vs. Spring AOT Optimization               |
+---------------------------------------------------------------------------------------+
| ❌ Standard JIT JVM Startup (30s+):                                                   |
| Classpath Scan ---> Runtime Reflection ---> CGLIB Proxy Gen ---> ApplicationReady     |
|                                                                                       |
| ✅ Spring Boot 3 AOT Startup (<1s in Native GraalVM / 3s JVM):                        |
| Pre-compiled Bean Definitions ---> Instant Initialization ---> ApplicationReady       |
+---------------------------------------------------------------------------------------+
```

### Enabling Spring AOT
In Spring Boot 3+, Ahead-Of-Time (AOT) optimizations pre-evaluate bean definitions at build time.
```bash
./gradlew bootJar -Pspring-boot.aot=true
```

---

## 3. Proactive Cache Preheating Architecture

When a new application instance initializes and registers with the service discovery mesh, hitting an empty in-memory cache with massive concurrent read traffic instantly triggers a **Cache Stampede** (thousands of concurrent threads querying the database simultaneously for the exact same missing cache key).

```java
@Component
@RequiredArgsConstructor
public class DomainCachePreheater implements ApplicationListener<ApplicationReadyEvent> {
    private final TenantRepository tenantRepository;
    private final CacheManager cacheManager;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Cache cache = cacheManager.getCache("tenantConfigs");
        if (cache == null) return;

        // Proactively populate local cache upon application startup before accepting traffic
        List<TenantConfig> configs = tenantRepository.findAllActiveConfigs();
        configs.forEach(c -> cache.put(c.getTenantId(), c));
    }
}
```

---

## 4. Performance Verification

```text
[ ] 1. Pool Tuning       : Verify HikariCP and Tomcat connection pools are explicitly tuned in production profiles.
[ ] 2. AOT Optimization  : Ensure container build pipelines leverage Spring AOT compilation.
[ ] 3. Stampede Guarding : Verify critical domain caches implement `ApplicationReadyEvent` preheating.
```
