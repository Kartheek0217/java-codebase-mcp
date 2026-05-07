# Virtual Threads + JDBC: Pinning Notes

Virtual threads can be pinned to platform threads when blocking operations occur inside synchronized regions or when native/JNI calls block without proper Loom integration. JDBC usage patterns and pool configuration should assume some pinning under load.

## Practical Guidance

- Prefer a JDBC driver and pool that do not hold monitors while performing network I/O.
- Avoid long synchronized blocks in request and indexing paths.
- Keep DB transactions short and avoid streaming ResultSets across long processing pipelines.
- Bound concurrency for DB work independently from file I/O concurrency.

## Pool Sizing Heuristics (Local Usage)

- Start with a small pool and increase based on observed queueing and pinned-thread signals.
- Keep pool size below the number of available cores for local usage unless profiling proves otherwise.

## Measurement

- Track platform thread utilization and pinned-thread events during concurrent scans.
- Track DB latency percentiles separately from file read latency.

