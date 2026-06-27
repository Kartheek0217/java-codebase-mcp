---
name: java-memory-gc
description: Detecting memory leaks, OOM diagnostics, ThreadLocal safety, heap tuning, and G1GC garbage collection tuning.
---

# Java Memory & GC

## 1. Leaks & ThreadLocal
- **No Unbounded Maps**: Never use `HashMap` or `ConcurrentHashMap` as static caches. Use bounded caches (`Caffeine`, `Guava`) with size limits and TTL.
- **ThreadLocal Cleanup**: Clear `ThreadLocal` variables in servlet filters or interceptors to avoid memory leaks in thread pools.
  ```java
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
      try {
          ContextHolder.set(extractUser(req));
          chain.doFilter(req, res);
      } finally {
          ContextHolder.clear(); // remove()
      }
  }
  ```

## 2. OOM Diagnostics
- **OOM Types**:
  - `Java heap space`: Bounded caches, query pagination.
  - `GC overhead limit exceeded`: Eliminate allocation thrash in loops.
  - `Metaspace`: ClassLoader leaks, dynamic proxy limits.
  - `Unable to create new native thread`: Bounded thread pools.
- **JVM Diagnostic Flags**:
  ```bash
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/var/log/jvm/heapdump.hprof
  -XX:+ExitOnOutOfMemoryError
  ```

## 3. Generational G1GC Tuning
- **Production Baseline (4GB - 64GB heaps)**:
  ```bash
  -XX:+UseG1GC -Xms16g -Xmx16g -XX:MaxGCPauseMillis=200 -XX:InitiatingHeapOccupancyPercent=45 -XX:G1ReservePercent=15
  ```
- **Invariants**:
  - Keep `-Xms == -Xmx` to avoid dynamic OS allocations.
  - Control humongous allocations (`>50%` region size) by increasing `-XX:G1HeapRegionSize=16m`.

## 4. Verification Checklist
- [ ] Caches have TTL or max size.
- [ ] ThreadLocal values cleared in a `finally` block.
- [ ] Production JVM flags include HeapDumpOnOutOfMemoryError.
- [ ] Min/Max heap sizes set to equal values.
