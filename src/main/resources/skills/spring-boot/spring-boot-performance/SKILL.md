---
name: spring-boot-performance
description: Boot time optimization, connection pooling, and JVM tuning.
---

# Spring Boot Performance

## 1. Connection Pool Sizing
- **HikariCP Tuning**: Calculate size based on CPU cores and disk spindles: `connections = ((cpu_cores * 2) + effective_spindle_count)`.
  ```properties
  spring.datasource.hikari.maximum-pool-size=20
  spring.datasource.hikari.minimum-idle=10
  ```

## 2. Startup Optimization
- **Lazy Initialization**: Enable lazy initialization for development or serverless environments if startup time is critical.
  ```properties
  spring.main.lazy-initialization=true
  ```

## 3. JVM Optimization
- Set GC pause time targets and tune sizing:
  ```bash
  -XX:+UseG1GC -XX:MaxGCPauseMillis=100
  ```

## 4. Verification Checklist
- [ ] Connection pool sizes adjusted for production workloads.
- [ ] Bean loading and scanning optimized (removed unused dependencies/scans).
