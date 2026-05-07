# Project Plan: java-codebase-mcp

This document consolidates the roadmap, technical tasks, and operational strategies for the MCP Repo Indexing Middleware.

## 🛠 Technology Stack
- **Core:** Spring Boot 4.0, JDK 25
- **Build System:** Maven (Standard repository dependencies)
- **Threading:** Java Virtual Threads (Project Loom) — *pinned-thread optimized*
- **Indexing:** Apache Lucene (MMapDirectory with custom chunk/unmap strategy)
- **Database:** H2 via **Liquibase** (Local usage only)
- **Parsing:** **Tree-sitter** (Native JNI bindings)
- **File Watching:** **JNotify** (Native) with robust `WatchService` fallback
- **Observability:** Prometheus + JSON Structured Logging
- **Caching:** Caffeine (`maximumWeight` capacity)

---

## 📅 Roadmap Overview

### Sprint 0: Discovery & Hard Gate Spikes
Validate native dependencies (Tree-sitter, JNotify) and Lucene MMap safety. **This is a hard gate phase.**

### Sprint 1: Infrastructure & Observability
Initialize project, configure virtual-thread JDBC pooling, and establish SLO metrics.

### Sprint 2: High-Throughput Scanning & Reconciliation
Implement robust scanning with binary sampling and full-scan event reconciliation.

### Sprint 3: Crash-Resilient Indexing & Search
Build a resilient Lucene index with snapshot/rebuild paths and definition-first ranking.

### Sprint 4: Code Intelligence & Security Hardening
AST-based symbol extraction, transitive dependency resolution, and API hardening.

---

## 📝 Detailed Sprint Tasks

### Sprint 0: Compatibility & Hard Gates
- [ ] **Local Environment Validation:** Verify Tree-sitter and JNotify compatibility on the local Windows environment.
- [ ] **Lucene MMap Spike:** Prototype MMapDirectory chunk-size tuning and unmap strategy; test Windows delete-on-close quirks.
- [ ] **Virtual Thread Benchmark:** Measure I/O latency and memory; document JDBC pinning risks and pool sizing best practices.
- [ ] **Gate Check:** Only proceed to Sprint 2 if local benchmarks and native loading pass validation.

### Sprint 1: Foundation & Observability Baseline
- [ ] Initialize Maven project; implement `VirtualThreadTaskExecutor`.
- [ ] Setup H2 with Liquibase; implement Prometheus exports.
- [ ] **JDBC Tuning:** Configure connection pools to mitigate virtual-thread pinning during I/O.
- [ ] **Observability Baseline:** Instrument metrics for index write latency, queue depth, and parse errors.

### Sprint 2: Scanning & Persistence
- [ ] `FileScanner` with Virtual Threads emitting events to a durable internal queue.
- [ ] **Binary Sampling:** 8KB entropy heuristic to prevent indexing non-text data.
- [ ] **Fidelity Testing:** Validate JNotify event fidelity; implement the `WatchService` secondary fallback.
- [ ] **Full-Scan Reconciliation:** Implement periodic rescan to reconcile missed file system events.

### Sprint 3: Resilient Indexing
- [ ] **Index Safety:** Implement WAL queue, single-writer policy, and periodic index snapshots.
- [ ] **Crash Recovery:** Test index snapshot + simulated writer crash tests locally.
- [ ] **Safe Reindex Path:** Implement logic to rebuild the Lucene index from H2 metadata.
- [ ] **Symbol Boosting:** Implement specialized ranking (definitions > usages).

### Sprint 4: Code Intelligence & Security
- [ ] **Tree-sitter Integration:** AST-based symbol extraction (gated by Sprint 0 results).
- [ ] **Dependency Skill:** Transitive resolution logic for package/module graphs.
- [ ] **Hardening:** Implement rate limiting, auth requirements, and query injection prevention.
- [ ] **Runbooks:** Finalize index recovery runbook and local maintenance guide.

---

## 📊 Risk / Mitigation Matrix

| **Risk** | **Impact** | **Mitigation (Sprint)** | **Acceptance Check** |
|---|---:|---|---|
| MMapDirectory Corruption | High | WAL queue + snapshots; single writer | Successful rebuild from H2 metadata |
| JNotify Missed Events | Medium | `WatchService` fallback + full scan | Zero unreconciled changes after 1 window |
| Native Load Failures | High | Local environment validation | Native parser loads correctly locally |
| Virtual Thread Pinning | Medium | Tune pool; use Project Loom best practices | SLOs met under concurrent I/O load |

---

## ✅ Acceptance Criteria & SLOs

- **Search Latency:** Average < 200ms at target dataset size.
- **Index Integrity:** No corruption after simulated writer crash; recovery verified.
- **Reconciliation:** Missed file events detected and reconciled within one scan window.
- **Observability:** Dashboards active for scan throughput, cache weight, and parse errors.

---

## 🚀 Concrete Next Steps
1. **Environment Setup:** Verify local Maven repository access and native dependency loading.
2. **Sprint 0 Spikes:** Execute Tree-sitter smoke test and virtual-thread I/O benchmark.
3. **Database:** Initialize Liquibase and H2 configuration for local persistence.
4. **Docs:** Start the operational runbook for index recovery procedures.
