# Project Plan: java-codebase-mcp (Updated)

This document consolidates the roadmap, technical tasks, and operational strategies for the MCP Repo Indexing Middleware. Updated to reflect the "Stripped Down" core-only implementation.

## 🛠 Technology Stack
- **Core:** Spring Boot 4.0.6, JDK 25
- **Build System:** Maven
- **Threading:** Java Virtual Threads (Project Loom)
- **Indexing:** Apache Lucene (MMapDirectory)
- **Database:** H2 via **Spring SQL Init** (`schema.sql`)
- **Parsing:** **JavaParser** (AST-based)
- **File Watching:** **Directory Watcher** (Planned alternative to JNotify)
- **Observability:** Spring Boot Actuator (Basic Health/Info)

---

## 📅 Roadmap Overview

### Sprint 0: Discovery & Hard Gate Spikes [COMPLETED]
Validate dependencies (JavaParser, Lucene, Virtual Threads). JNotify identified as a blocker due to native library requirements.

### Sprint 1: Core Infrastructure [COMPLETED]
Initialize project, configure virtual-thread executor, and setup H2 with basic schema.

### Sprint 2: High-Throughput Scanning & Reconciliation
Implement robust scanning with Directory Watcher and full-scan event reconciliation.

### Sprint 3: Crash-Resilient Indexing & Search
Build a resilient Lucene index with snapshot/rebuild paths and definition-first ranking.

### Sprint 4: Code Intelligence
AST-based symbol extraction and transitive dependency resolution.

---

## 📝 Detailed Sprint Tasks

### Sprint 0: Compatibility & Hard Gates [DONE]
- [x] **Local Environment Validation:** Verify JDK 25 and Maven compatibility.
- [x] **JavaParser Spike:** Successfully parsed Java files and extracted symbols.
- [x] **Lucene MMap Spike:** Verified MMapDirectory stability and performance.
- [x] **Virtual Thread Benchmark:** Confirmed high-concurrency I/O performance.
- [x] **Gate Check:** Documented JNotify failure; recommendation to use `directory-watcher`.

### Sprint 1: Foundation [DONE]
- [x] Initialize Maven project (Spring Boot 4.0.6).
- [x] Implement `VirtualThreadTaskExecutor`.
- [x] Setup H2 with `schema.sql` initialization.
- [x] Implement `/status` and `/health` endpoints.

### Sprint 2: Scanning & Persistence
- [ ] `FileScanner` with Virtual Threads.
- [ ] **Directory Watcher:** Integrate `io.methvin:directory-watcher`.
- [ ] **Full-Scan Reconciliation:** Implement periodic rescan to reconcile missed file system events.

### Sprint 3: Resilient Indexing
- [ ] **Index Safety:** Implement single-writer policy and periodic index snapshots.
- [ ] **Crash Recovery:** Test index recovery after simulated failure.
- [ ] **Safe Reindex Path:** Implement logic to rebuild the Lucene index from H2 metadata.

### Sprint 4: Code Intelligence
- [ ] **AST Symbol Extraction:** Deep symbol parsing using JavaParser.
- [ ] **Dependency Graph:** Transitive resolution logic for package/module graphs.

---

## 📊 Risk / Mitigation Matrix

| **Risk** | **Impact** | **Mitigation** | **Status** |
|---|---:|---|---|
| MMapDirectory Corruption | High | Single writer + Snapshots | Planned |
| Native Library Blocker | High | Switch to pure-Java alternatives | JNotify dropped |
| Virtual Thread Pinning | Medium | Avoid `synchronized` in I/O paths | Ongoing |

---

## ✅ Acceptance Criteria (Minimal)
- **Search Latency:** Average < 200ms.
- **Index Integrity:** No corruption after crash; recovery verified.
- **Reconciliation:** Missed file events detected and reconciled.
