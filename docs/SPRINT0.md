# Sprint 0: Discovery & Hard Gate Spikes

Sprint 0 is a hard gate. It is meant to validate native loading (Tree-sitter, JNotify), confirm Lucene MMap behavior, and produce basic virtual-thread I/O numbers before deeper indexing work starts.

## Local Setup

```bash
bash scripts/sprint0.sh vtio --fileCount=1 --fileSizeKb=1
```

## Spike: Native Dependency Loading

Runs a best-effort load of native libraries and (optionally) checks that expected Java classes exist on the classpath. This runner never fails the process; it writes a report you can attach to a sprint-gate decision.

```bash
bash scripts/sprint0.sh native
```

Optional inputs:
- `--libs=tree-sitter,jnotify`
- `--paths=C:\path\to\dlls` (Windows), or `--path /some/dir --path /another/dir`
- `-Dsprint0.native.tree-sitter.file=C:\full\path\to\tree-sitter.dll`
- `-Dsprint0.native.jnotify.file=C:\full\path\to\jnotify.dll`
- `-Dsprint0.native.tree-sitter.class=...` / `-Dsprint0.native.jnotify.class=...`

Output:
- `target/sprint0/native-validation.json`

## Spike: Lucene MMap Safety

Creates and closes an index under an MMapDirectory, then attempts to delete the directory tree. On Windows, deletion failures after close are a signal that unmapping strategy needs work.

```bash
bash scripts/sprint0.sh mmap --fileSizeMb=256 --chunkSizeMb=64
```

Output:
- `target/sprint0/lucene-mmap-spike.json`

## Spike: Virtual Thread I/O Benchmark

Generates a file set and reads it concurrently using virtual threads. Use this as a baseline for scan throughput expectations and to inform queue sizing.

```bash
bash scripts/sprint0.sh vtio --fileCount=2000 --fileSizeKb=64
```

Output:
- `target/sprint0/virtual-thread-io-benchmark.json`

## Gate Check

Capture the following in your Sprint 0 decision:
- Native libraries can be loaded on the target OS without manual hacks that are unacceptable for local usage.
- Lucene MMap open/close/delete behavior is acceptable for local Windows workflows.
- Virtual-thread I/O numbers are acceptable for the expected repo sizes and hardware.
