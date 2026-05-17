# Java G1 Garbage Collector Architecture & Tuning Guide

In high-throughput enterprise JVM environments running multi-gigabyte memory heaps, unpredictable garbage collection (GC) pauses can cause severe latency spikes, socket timeouts, and degraded user experiences. 

The **G1 (Garbage-First) Garbage Collector**—the default JVM collector starting from Java 9—was engineered to replace the legacy CMS (Concurrent Mark-Sweep) collector. Rather than dividing the heap into contiguous physical generations, G1 partitions the heap into equal-sized memory regions. By dynamically evaluating region reclamation costs, G1 selectively targets regions containing the most garbage first, delivering predictable, soft-bounded pause times (`MaxGCPauseMillis`).

```text
+-----------------------------------------------------------------------+
|                    G1GC Heap Region Grid Architecture                 |
+-----------------------------------------------------------------------+
|  [ Eden  ]  [ Eden  ]  [ Surv  ]  [ Old   ]  [ Old   ]  [ Free  ]     |
|  [ Free  ]  [ Surv  ]  [ Old   ]  [ Humongous (50%+ region size) ]    |
|  [ Eden  ]  [ Free  ]  [ Old   ]  [ Old   ]  [ Free  ]  [ Eden  ]     |
+-----------------------------------------------------------------------+
|  Key: [ Eden ] = Young Gen   [ Surv ] = Survivor   [ Old ] = Tenured  |
+-----------------------------------------------------------------------+
```

---

## 1. Architectural Region Partitioning

Internally, G1 manages physical memory as an array of independent regions ranging from $1\text{MB}$ to $32\text{MB}$ (determined dynamically during JVM boot based on total heap size). While physical layout is non-contiguous, G1 conceptually assigns regions to 4 distinct operational roles:

```text
+------------------------+-------------------------------+----------------------------------------+
| Region Role            | Architectural Purpose         | Evacuation & Lifecycle Dynamics        |
+------------------------+-------------------------------+----------------------------------------+
| Eden Regions           | Newly allocated object instances| Swept entirely during Young GC pauses. |
| Survivor Regions       | Objects surviving a minor GC  | Stepping stone before Old Gen tenuring.|
| Old (Tenured) Regions  | Long-lived domain singletons  | Collected during Mixed GC sweeps.      |
| Humongous Regions      | Monolithic objects (>50% size)| Allocated directly in Old Gen regions. |
+------------------------+-------------------------------+----------------------------------------+
```

> [!WARNING]  
> **The Humongous Allocation Trap:** When an object exceeds $50\%$ of a single region's size (e.g., allocating a $10\text{MB}$ byte buffer when region size is $16\text{MB}$), G1 allocates it directly across contiguous Humongous Old regions. Humongous allocations bypass Eden entirely and can trigger severe memory fragmentation and premature Full GC halts.

---

## 2. In-Memory Simulation (`G1GCExample.java`)

The following simulation demonstrates how object lifecycles transition across Eden, Survivor, Old, and Humongous regions.

```java
package com.example.demo.memory;

import java.util.ArrayList;
import java.util.List;

public class G1GCExample {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Initializing G1GC Memory Simulation...");

        // 1. Eden Allocation: Allocate short-lived temporary objects
        for (int i = 0; i < 1000; i++) {
            // Allocates 50 KB per object in Eden space
            byte[] edenObject = new byte[1024 * 50]; 
        }

        // Allow GC threads to execute minor young evacuation
        Thread.sleep(1000); 

        // 2. Survivor Allocation: Objects held in active scope survive minor sweeps
        List<byte[]> survivorHolder = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            // Allocates 100 KB objects
            survivorHolder.add(new byte[1024 * 100]); 
        }

        Thread.sleep(1000); 

        // 3. Old Gen Tenuring: Continuously held objects exceed tenuring thresholds
        List<byte[]> tenuredHolder = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            // Allocates 512 KB objects
            tenuredHolder.add(new byte[1024 * 512]); 
            // Introduce aging delay to simulate long-lived cache retention
            Thread.sleep(10); 
        }

        // 4. Humongous Allocation: Monolithic object exceeding 50% region size
        // Allocates a massive 4 MB contiguous buffer directly in Humongous Old Gen
        byte[] humongousBuffer = new byte[1024 * 1024 * 4]; 

        System.out.println("Simulation complete. Retaining object roots to observe GC logs.");
        Thread.sleep(10000);
    }
}
```

---

## 3. Execution CLI & Log Diagnostics

To execute the simulation and inspect real-time G1 memory movement across region boundaries, enable unified JVM GC logging (`-Xlog:gc*`).

```bash
# Execute simulation with 200MB bounded heap and G1 unified logging
java -Xmx200m -Xms200m -XX:+UseG1GC -Xlog:gc* com.example.demo.memory.G1GCExample
```

### 🔬 Sample G1 GC Unified Log Breakdown

```text
[0.012s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 16M->2M(200M) 11.100ms
[0.012s][info][gc,heap] GC(0) Eden: 16M->0B(100M) Survivors: 0B->2M(12M) Old: 0B->0B(100M) Humongous: 4M->4M(200M)
```

```text
[ Raw Allocation ] ---> [ Evacuation Pause ] ---> Eden Emptied (16M -> 0B)
                                           ---> Survivors Allocated (0B -> 2M)
                                           ---> Humongous Untouched (4M -> 4M)
```

- **`Pause Young (G1 Evacuation Pause)`:** Indicates Eden capacity was fully exhausted, triggering parallel worker threads to evacuate live objects into Survivor regions.
- **`Eden: 16M->0B`:** All Eden regions were completely reclaimed.
- **`Survivors: 0B->2M`:** Surviving objects were successfully copied into Survivor regions.
- **`Humongous: 4M->4M`:** The monolithic 4MB buffer remains anchored in Old Humongous regions.

---

## 4. Behind the Scenes: The 4 Core G1GC Phases

```text
+-------------------------------------------------------------------------------------------------+
|                                     G1GC Operational Lifecycle                                  |
+-------------------------------------------------------------------------------------------------+
| [ 1. Young GC ] ---> [ 2. Concurrent Marking ] ---> [ 3. Mixed GC ] ---> [ 4. Full GC (Fallback)]
| - Cleans Eden        - Background Old Tracking      - Cleans Eden+Old    - Monolithic STW Halt  |
| - STW Evacuation     - SATB Snapshotting            - Soft Pause Bounded - Triggered by OOM     |
+-------------------------------------------------------------------------------------------------+
```

### Phase 1: Young GC (Minor Evacuation)
Triggered when available Eden regions are fully allocated. G1 halts application threads (Stop-The-World), activates parallel worker threads, and copies live objects from Eden into Survivor regions.

---

### Phase 2: Concurrent Marking
Tracks live objects across Old generation regions in the background while application threads continue execution. G1 utilizes **SATB (Snapshot At The Beginning)** to take a logical snapshot of object graphs at the start of marking.

1. **Initial Mark (STW):** Halts threads briefly to identify primary GC roots.
2. **Root Region Scan:** Concurrently inspects Survivor regions for references pointing into Old Gen.
3. **Concurrent Mark:** Traverses all reachable object graphs across Old regions.
4. **Remark (STW):** Brief STW halt to finalize SATB marking buffers.
5. **Cleanup:** Immediately reclaims regions discovered to contain $100\%$ dead garbage.

---

### Phase 3: Mixed GC (The Core G1 Innovation)
Following concurrent marking, G1 initiates a Mixed GC. Rather than sweeping the entire heap, G1 evacuates all Eden regions plus a specifically selected subset of Old regions possessing the highest garbage reclamation yield ($>85\%$ garbage).

```bash
# Soft pause target guiding G1 region selection during Mixed sweeps
-XX:MaxGCPauseMillis=100
```
If evacuating 50 Old regions would exceed the 100ms pause target, G1 dynamically reduces the sweep scope to 30 regions, deferring remaining regions to subsequent Mixed cycles.

---

### Phase 4: Full GC (Emergency Fallback)
If application thread allocation rates drastically exceed G1 evacuation speeds or severe Humongous fragmentation occurs, G1 triggers an emergency, single-threaded Stop-The-World Full GC sweep across the entire heap.

---

## 5. Production Tuning Blueprint

```bash
# High-Throughput / Low-Latency Enterprise G1GC Execution Template
exec java -server \
    -Xms16g -Xmx16g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:InitiatingHeapOccupancyPercent=45 \
    -XX:G1ReservePercent=15 \
    -XX:G1HeapRegionSize=32m \
    -XX:+ParallelRefProcEnabled \
    -XX:+AlwaysPreTouch \
    -Xlog:gc*=info,gc+phases=debug:file=/var/log/jvm/gc.log:time,uptime,pid:filecount=10,filesize=50M \
    -jar /opt/app/enterprise-service.jar
```

```text
+---------------------------------------+-------------------------------------------------------+
| Tuning Parameter                      | Architectural Production Impact                       |
+---------------------------------------+-------------------------------------------------------+
| `-XX:MaxGCPauseMillis=100`            | Soft target instructing G1 to throttle evacuation     |
|                                       | scopes to keep STW pauses under 100ms.                |
| `-XX:InitiatingHeapOccupancyPercent`  | Triggers background Concurrent Marking when total heap|
|                                       | occupancy reaches 45% (prevents late Full GCs).       |
| `-XX:G1ReservePercent=15`             | Reserves 15% of heap capacity as a buffer to prevent  |
|                                       | promotion failures during heavy object promotion.     |
| `-XX:G1HeapRegionSize=32m`            | Sets explicit 32MB regions to accommodate large buffers|
|                                       | and prevent frequent Humongous allocations.           |
+---------------------------------------+-------------------------------------------------------+
```

---

## 6. Summary Verification Checklist

```text
[ ] 1. Sizing Boundaries: Set -Xms exactly equal to -Xmx to prevent runtime resizing stalls.
[ ] 2. Pause Targets    : Define realistic pause SLAs via -XX:MaxGCPauseMillis (default is 200ms).
[ ] 3. Humongous Audit  : Inspect unified GC logs for Humongous region spikes; increase region size.
[ ] 4. Marking Threshold: Tune IHOP (-XX:InitiatingHeapOccupancyPercent) if Full GCs occur.
[ ] 5. Unified Logging  : Always attach -Xlog:gc* in production to record historical evacuation metrics.
```
