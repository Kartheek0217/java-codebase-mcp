# Spring Boot High-Throughput Production Tuning Guide: Scaling to 1M+ RPS

Out-of-the-box Spring Boot configurations and default JVM settings are exceptionally reliable for standard, moderate workloads. However, when engineering enterprise backend services targeted at extreme throughput—approaching or exceeding **1,000,000 Requests Per Second (RPS)**—the default configurations prove far too conservative. Under massive concurrent load, unoptimized services experience severe garbage collection pauses, thread pool starvation, and cascading database connection exhaustion.

This guide details three foundational production pillars required to scale Spring Boot 3.x services for extreme throughput: JVM Garbage Collection & Heap Optimization, Project Loom Virtual Threads, and Database Connection Pool Tuning.

---

## 1. Architectural Overview: The High-Throughput Triad

```text
+-------------------------------------------------------------------------------+
|                       1M+ Requests Per Second (RPS)                           |
+-------------------------------------------------------------------------------+
                                        |
      +---------------------------------+---------------------------------+
      |                                 |                                 |
      v                                 v                                 v
[ Pillar 1: JVM Tuning ]     [ Pillar 2: Virtual Threads ]  [ Pillar 3: HikariCP Tuning ]
- Predictable GC Pause       - Spring Boot 3.2+ / Loom      - Bounded Connection Pools
- Memory Page Pre-touching   - M:N Carrier Thread Mapping   - DB Overload Protection
- Low-Latency Heap Sizing    - Eliminates OS Thread Limits  - Sub-millisecond Checkout
```

---

## 2. Pillar 1: JVM Production Tuning

Default JVM ergonomics prioritize general execution balance. Under extreme throughput, memory allocation rates skyrocket, requiring explicit tuning for predictable garbage collection, low-latency object allocation, and stable heap allocation boundaries.

### Recommended Baseline JVM Production Flags

```bash
# High-Throughput G1GC Production Baseline
java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=50 \
     -XX:+AlwaysPreTouch \
     -XX:+UseStringDeduplication \
     -jar target/high-throughput-service.jar
```

```text
+-----------------------------+-------------------------------------------------------+
| JVM Flag                    | Operational Production Impact                         |
+-----------------------------+-------------------------------------------------------+
| -Xms4g -Xmx4g               | Setting min heap equal to max heap eliminates         |
|                             | expensive runtime heap expansion and contraction halts|
+-----------------------------+-------------------------------------------------------+
| -XX:+UseG1GC                | Activates Garbage-First GC, optimized for multi-gig   |
|                             | heaps and partitioning memory into manageable regions.|
+-----------------------------+-------------------------------------------------------+
| -XX:MaxGCPauseMillis=50     | Sets a soft target for GC pauses to 50ms, instructing |
|                             | G1GC to adjust collection frequency to protect latency|
+-----------------------------+-------------------------------------------------------+
| -XX:+AlwaysPreTouch         | Proactively touches all memory pages during JVM boot, |
|                             | forcing the OS to allocate physical RAM instantly and |
|                             | preventing runtime page-fault stalls during live load.|
+-----------------------------+-------------------------------------------------------+
| -XX:+UseStringDeduplication | Instructs the garbage collector to inspect duplicate  |
|                             | string instances and point them to single shared char |
|                             | arrays, drastically reducing heap memory footprint.   |
+-----------------------------+-------------------------------------------------------+
```

### Ultra-Low Latency Workloads: Generational ZGC

When P99.9 latency consistency is strictly required over raw batch throughput, replace G1GC with **ZGC (Z Garbage Collector)**.

```bash
# Ultra-Low Latency Generational ZGC Baseline (JDK 21+)
java -Xms8g -Xmx8g \
     -XX:+UseZGC \
     -XX:+UseGenerationalZGC \
     -XX:+AlwaysPreTouch \
     -jar target/ultra-low-latency-service.jar
```

**Why ZGC Matters:** Generational ZGC performs all expensive marking and compaction work concurrently with application threads. GC pauses consistently stay below **1 millisecond**, regardless of whether your heap size is 4GB or 16TB.

---

## 3. Pillar 2: Loom Virtual Threads (Spring Boot 3.2+)

Project Loom Virtual Threads represent one of the greatest performance transformations in modern Spring Boot architecture. Traditionally, Spring MVC binds one operating system (OS) platform thread to every incoming HTTP request (`spring.mvc.threads.max=200`). Under high concurrency, thread pools quickly exhaust, leading to HTTP `503 Service Unavailable` failures and massive context-switching overhead.

### Enabling Virtual Threads in `application.yml`

```yaml
spring:
  threads:
    virtual:
      enabled: true # Instantly replaces standard OS thread pools with Virtual Threads
```

### Architectural Mechanics: Virtual vs. Platform Threads

```text
+---------------------------------------------------------------------------------+
| Standard OS Platform Threads (`spring.threads.virtual.enabled=false`)           |
| [Request 1] ---> [OS Platform Thread 1] (Blocks OS thread during DB I/O)        |
| [Request 2] ---> [OS Platform Thread 2] (Blocks OS thread during HTTP call)     |
| (Max 200 OS threads -> Incoming Request 201 queues or fails with 503 HTTP error)|
+---------------------------------------------------------------------------------+

+---------------------------------------------------------------------------------+
| Loom Virtual Threads (`spring.threads.virtual.enabled=true`)                    |
| [Request 1] --VirtualThread_1--+                                                |
| [Request 2] --VirtualThread_2--+--> [Carrier Thread Pool: OS Threads = CPU Cores]
| [Request N] --VirtualThread_N--+ (Unmounts virtual thread instantly during I/O) |
+---------------------------------------------------------------------------------+
```

> [!IMPORTANT]  
> **The Real Value of Virtual Threads:** Virtual threads do not magically eliminate the underlying network or database latency of blocking I/O calls. Instead, they eliminate the OS thread allocation and context-switching bottlenecks. A single Spring Boot JVM can easily manage **1,000,000 active virtual threads** simultaneously on standard hardware.

---

## 4. Pillar 3: HikariCP Connection Pool Tuning

When a Spring Boot service handling 1M RPS executes database transactions, the relational database connection pool becomes the single greatest operational bottleneck. Spring Boot uses **HikariCP** by default due to its zero-overhead bytecode engineering and sub-millisecond connection acquisition times.

### The Connection Pool Trap

```text
+-------------------------------------------------------------------------------+
|                      HikariCP Production Configurations                       |
+-------------------------------------------------------------------------------+
       |                                                 |
       v                                                 v
[ Pool Too Small: max=10 ]                      [ Pool Too Large: max=1000 ]
- Threads queue waiting for connection.         - Database connection limits hit.
- Application latency spikes uncontrollably.    - DB CPU collapses due to thread thrash.
```

### Optimal Production HikariCP Configuration (`application.yml`)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50      # Maximum physical connections to open to the database
      minimum-idle: 20           # Minimum connections maintained in the pool during low traffic
      connection-timeout: 2000   # Max time (2s) a thread will wait for a connection before failing
      idle-timeout: 30000        # Time (30s) before an idle connection is closed and evicted
      max-lifetime: 600000       # Time (10m) before a connection is forcibly retired to prevent leaks
```

### The Golden Rule of Connection Pool Sizing

> [!CAUTION]  
> **Increasing connection pool size does not increase database IOPS or processing capacity. It only increases how fast you overload the database.**

If a relational database can only process 50 concurrent disk execution threads efficiently before CPU thrashing occurs, setting `maximum-pool-size=500` across multiple application pods will instantly collapse the database cluster under heavy load. Pool tuning must strictly match the maximum physical processing capacity of the database server.

---

## 5. High-Throughput Production Verification Checklist

```text
[ ] 1. JVM Heap Boundaries  : Verify -Xms is exactly equal to -Xmx on all pods.
[ ] 2. Memory Pre-touching  : Ensure -XX:+AlwaysPreTouch is enabled to eliminate page faults.
[ ] 3. Garbage Collector    : Confirm G1GC or Generational ZGC is active via JVM logs.
[ ] 4. Virtual Threads      : Verify spring.threads.virtual.enabled=true in active profiles.
[ ] 5. HikariCP Bounding    : Confirm maximum-pool-size is bounded to DB CPU core capacity.
[ ] 6. Connection Timeout   : Keep connection-timeout <= 2000ms to fail fast during outages.
```
