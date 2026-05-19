---
name: java-collections
description: Selecting, pre-sizing, and executing high-performance Java collections under low-latency and high-concurrency constraints.
---

# Java Collections

## 1. Selection Invariants
- **No LinkedList**: Atrocious CPU cache locality due to scattered node heap allocation. Use `ArrayList` or `ArrayDeque`.
- **No Vector/Hashtable**: Synchronized methods degrade throughput. Use concurrent variants.
- **Selection**:
  - `ArrayList`: O(1) index access, O(n) search/delete.
  - `ArrayDeque`: O(1) head/tail.
  - `HashMap`/`HashSet`: O(1) avg lookup/insert.
  - `TreeMap`/`TreeSet`: O(log n) sorted operations.

## 2. Pre-Sizing Formula
- **HashMap/HashSet**: Omitting initial capacity forces expensive array resizing/rehashing. Set initial capacity = `Math.ceil(Elements / 0.75)`.
  ```java
  Map<String, User> map = new HashMap<>((int) Math.ceil(expectedSize / 0.75));
  ```
- **ArrayList**: Pre-size to avoid array copying on growth.
  ```java
  List<Item> list = new ArrayList<>(expectedSize);
  ```

## 3. Concurrency
- `ConcurrentHashMap`: Lock-free reads, CAS operations, and striped bucket-level locks. Use `computeIfAbsent`.
  ```java
  map.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
  ```
- `CopyOnWriteArrayList`: Read-heavy, write-expensive (copies entire backing array).
- `ConcurrentSkipListMap`: Concurrent sorted navigable map.
- `ArrayBlockingQueue`: Bounded queue for producer-consumer threads.

## 4. Verification Checklist
- [ ] No `LinkedList`, `Vector`, or `Hashtable`.
- [ ] Known-size Maps and Lists use explicit initial capacities.
- [ ] Shared mutable collections use thread-safe concurrent variants.
