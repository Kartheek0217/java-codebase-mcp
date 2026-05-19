---
name: java-memory-gc
description: >
  Detecting memory leaks, OOM diagnostics, ThreadLocal safety, heap tuning, and G1GC garbage collection tuning.
---

# Java Memory Architecture & Garbage Collection Guide

This guide establishes rigorous standards for JVM memory management: identifying memory leaks, safely managing thread-local contexts, preventing OutOfMemoryError crashes, and tuning generational garbage collection (G1GC) for low-latency enterprise workloads.

---

## 1. Memory Leak Identification & ThreadLocal Management

In managed runtimes like the JVM, memory leaks occur when long-lived object references unintentionally retain child object graphs that are no longer actively used by the application, preventing the Garbage Collector from reclaiming heap space.

```text
+---------------------------------------------------------------------------------------+
|                            The Classic Unbounded Cache Leak                           |
+---------------------------------------------------------------------------------------+
| Unbounded Static Map:                                                                 |
| [ Root Reference (Class Loader) ] ---> [ static Map<String, UserCache> ]              |
|                                                     |                                 |
| Retains 5,000,000 inactive User objects forever ---> OutOfMemoryError: Java heap space|
+---------------------------------------------------------------------------------------+
```

### The Unbounded Cache Invariant
> Never instantiate an in-memory cache using plain `HashMap` or `ConcurrentHashMap`. Always utilize an explicit caching library (`Caffeine`, `Guava`) configured with maximum size bounds and time-to-live (TTL) eviction policies.

### ThreadLocal Memory Leaks in Thread Pools
When executing inside web server thread pools (Tomcat, Jetty), threads are pooled and reused across thousands of distinct HTTP requests. If a thread sets a `ThreadLocal` variable and fails to clear it before returning to the pool, the stored object remains referenced by the thread's internal map forever.

```java
public class SecurityContextHolder {
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(UserContext context) { CONTEXT.set(context); }
    public static UserContext getContext() { return CONTEXT.get(); }
    public static void clearContext() { CONTEXT.remove(); }
}

// ✅ Correct Pattern: Enforce strict try-finally cleanup inside Servlet Filters
public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
    try {
        SecurityContextHolder.setContext(extractUser(req));
        chain.doFilter(req, res);
    } finally {
        SecurityContextHolder.clearContext(); // Guaranteed thread-local cleanup!
    }
}
```

---

## 2. OutOfMemoryError (OOM) Prevention Architecture

An `OutOfMemoryError` is a fatal JVM exception indicating that heap space or native memory is completely exhausted.

```text
+---------------------------------------------------------------------------------------+
|                       Primary OutOfMemoryError Categories                             |
+---------------------------------------+-----------------------------------------------+
| OOM Subtype                           | Root Cause & Resolution Pattern               |
+---------------------------------------+-----------------------------------------------+
| Java heap space                       | Unbounded collections / heavy entity queries. |
|                                       | Enforce pagination & bounded caches.          |
| GC overhead limit exceeded            | JVM spends >98% time in GC freeing <2% heap.  |
|                                       | Eliminate allocation thrash in hot loops.     |
| Metaspace                             | ClassLoader leaks (dynamic proxy generation). |
|                                       | Limit dynamic runtime bytecode compilation.   |
| unable to create new native thread    | Exhausted OS thread limits (ulimit).          |
|                                       | Switch to bounded Executor pools or Loom.     |
+---------------------------------------+-----------------------------------------------+
```

### Production Crash Diagnostics Flags
Every production JVM must launch with automated diagnostic flags enabled to capture heap snapshots instantly upon OOM failure.

```bash
# Required Production JVM Flags
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/jvm/heapdump.hprof
-XX:+ExitOnOutOfMemoryError
```

---

## 3. Generational Garbage Collection & G1GC Tuning

The JVM heap is partitioned into generations based on the **Weak Generational Hypothesis**: the vast majority of allocated objects die shortly after creation (e.g., local method variables, DTOs).

```text
+---------------------------------------------------------------------------------------+
|                               G1GC Heap Region Layout                                 |
+---------------------------------------------------------------------------------------+
| [ Eden ] [ Survivor ] [ Tenured (Old) ] [ Humongous (>50% Region Size) ] [ Free ]     |
|                                                                                       |
| G1GC divides the heap into 2,048 equal regions (1MB - 32MB).                          |
| Allocations >50% of region size go directly to Humongous regions, causing fragmentation.|
+---------------------------------------------------------------------------------------+
```

### Production G1GC Configuration Baseline
For modern enterprise microservices with heap sizes between $4\text{GB}$ and $64\text{GB}$, G1GC is the optimal balanced collector.

```bash
# Recommended G1GC Production Baseline
-XX:+UseG1GC
-Xms16g -Xmx16g
-XX:MaxGCPauseMillis=200
-XX:InitiatingHeapOccupancyPercent=45
-XX:G1ReservePercent=15
```

### Configuration Invariants
1. **Identical Heap Bounds (`-Xms == -Xmx`):** Setting initial heap equal to maximum heap eliminates expensive runtime OS memory allocation pauses during traffic spikes.
2. **Humongous Allocation Control:** If profiling reveals high Humongous allocations, increase region size (`-XX:G1HeapRegionSize=16m`) to prevent premature Old Gen fragmentation.

---

## 4. Memory Architecture Verification

```text
[ ] 1. Cache Auditing     : Ensure zero plain Map objects are utilized for static or long-lived data caches.
[ ] 2. ThreadLocal Cleanup: Audit all custom `ThreadLocal` classes for guaranteed `try-finally` removal.
[ ] 3. Diagnostic Flags   : Verify production JVM launch scripts include HeapDumpOnOutOfMemoryError.
[ ] 4. G1GC Optimization  : Enforce equal `-Xms` and `-Xmx` sizing alongside explicit pause time SLAs.
```
