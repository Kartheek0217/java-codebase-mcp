# Project Review — `java-codebase-mcp`

> **Reviewed**: 2026-06-11  
> **Stats**: 153 files · 906 symbols · Spring Boot 4.0 · JDK 25  
> **Session**: `aafc93e5-28f1-4167-9ee6-5b6c187c7ea2`

---

## Architecture Overview

```
Controllers (REST/MCP)
    ├── CodebaseController   → file indexing, search, symbols, topology, batch
    ├── McpController        → sessions, tasks, rules, skills
    ├── LlmController        → LLM actions (explain, review, refactor, test-gen)
    ├── ProjectController    → CRUD, reindex, git-status, stage/commit
    ├── BrowserController    → Playwright headless browser sessions
    └── SystemController     → health, LLM status

Services (Core)
    ├── FileIndexerService          → JavaParser AST + multi-language regex extraction
    ├── LuceneIndexService          → per-project Lucene index (SearcherManager + IndexWriter)
    ├── FileDataPersistenceService  → symbol/call persistence
    ├── FileScannerService          → .gitignore-aware filesystem scan
    ├── ReconciliationService       → stale index cleanup
    ├── ProjectService              → project lifecycle + async post-commit indexing
    └── ContextMemoryService        → Caffeine-backed session memory

Services (LLM)
    ├── LlmService                  → facade
    ├── LlmClient / OllamaClient    → HTTP clients
    ├── LlmPromptBuilder            → prompt construction
    ├── LlmStreamingService         → SSE streaming
    └── WebSearchOrchestrator + HeadlessBrowserService

Persistence (JPA Entities)
    └── Project, Symbol, SymbolCall, FileMetadata,
        ProjectTask, TaskStep, ProjectRule, Skill, BrowserSession
```

---

## Strengths ✅

### 1. Clean Layering
Controllers → Services → Repositories pattern is well-maintained. Business logic doesn't bleed into controllers — `ProjectService.buildProjectOpResponse`, `getProjectStats`, and `getAllProjectSummaries` are good examples of logic properly extracted from the controller layer.

### 2. Dual Indexing Strategy
Combining **JavaParser AST** (precise, Java-only) with **Lucene full-text** (polyglot, fast search) is the right architecture. Bulk-mode optimization (`setBulkMode` + deferred `commitAndRefresh`) during initial indexing is a solid performance win.

### 3. Virtual Threads for Batch Operations
```java
// CodebaseController.java
private static final Executor BATCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
```
Correct use of JDK 21+ virtual threads for I/O-bound parallel file reads in `getBatchContext`.

### 4. ETag / 304 Support
`codebaseRead` supports `If-None-Match` + checksum-based ETags — avoids re-sending unchanged file content to LLM tools. Smart token optimization for agent workflows.

### 5. Multi-Language Symbol Extraction
`FileIndexerService` handles Java (AST), JS/TS, JSON, CSS, and HTML via dedicated patterns (`JS_PATTERN`, `JSON_PATTERN`, `CSS_PATTERN`, `ID_PATTERN`). Good breadth for a polyglot codebase tool.

### 6. `.gitignore`-Aware Scanner
`FileScannerService` uses `org.eclipse.jgit.ignore.IgnoreNode` — correctly skips `node_modules/`, `target/`, etc. without reimplementing gitignore parsing from scratch.

### 7. Skill System
SKILL.md frontmatter-driven skill loading (global + per-project) is an elegant extensibility mechanism. The JCB skill auto-loads on project creation, which is a strong UX default.

### 8. Search Timeout Guard
```java
// LuceneIndexService.java
private static class IndexSearcherTimeout implements QueryTimeout { ... }
```
Prevents runaway Lucene queries from blocking virtual threads. Good defensive design.

### 9. Post-Commit Async Indexing
`ProjectService.createProject` registers a `TransactionSynchronizationAdapter.afterCommit` callback to trigger file scanning only after the DB transaction commits — avoids race conditions between persistence and indexing.

---

## Issues & Recommendations 🔴

### 1. In-Memory `sessionStore` in `McpController` *(High Priority)*

**Problem**: Sessions are stored in a bounded `LinkedHashMap` — lost on restart, not shareable across instances, no durable TTL enforcement.

```java
// McpController.java
private final Map<String, Session> sessionStore; // ← in-memory only
```

**Fix**: Persist sessions to the DB (the `BrowserSession` entity is an existing pattern to follow) or use a Redis store. At minimum, document the single-instance constraint explicitly in the README.

---

### 2. Zero Test Coverage *(High Priority)*

**Problem**: `src/test/resources` is empty; no test classes exist anywhere. Core services like `FileIndexerService` and `LuceneIndexService` carry significant logic with no safety net.

**Fix**: Add at minimum:

| Test Class | Scope |
|---|---|
| `FileIndexerServiceTest` | Parse a sample `.java` file, assert correct symbols extracted |
| `LuceneIndexServiceTest` | Index + search round-trip with real on-disk index |
| `ProjectServiceTest` | Project create/delete lifecycle via `@DataJpaTest` |
| `CodebaseControllerTest` | `@WebMvcTest` for each `X-Op` value |

---

### 3. Virtual Thread Carrier Pinning (Traps in `BrowserSessionManager`, `GitInfoService`, `LuceneIndexService`) *(High Priority)*

**Problem**: Virtual threads are enabled (`spring.threads.virtual.enabled=true`). Executing blocking operations inside `synchronized` blocks/methods will **pin** the virtual thread to its carrier OS thread.

Specific pinning traps:
- **`BrowserSessionManager`**: `createSession()` and `ensurePlaywrightInitialized()` are synchronized methods that spawn browser processes (`Playwright.create()`, `chromium().launch()`), executing heavy blocking network/socket/IPC I/O.
- **`GitInfoService`**: `getRepository()` uses `synchronized (("repo-lock-" + projectId).intern())` around file-system checks and database queries.
- **`LuceneIndexService`**: `getWriter()` utilizes `synchronized (this)` around directory creation (`Files.createDirectories`) and Lucene `IndexWriter` instantiation.

**Fix**: Replace synchronized blocks/methods wrapping blocking I/O with `java.util.concurrent.locks.ReentrantLock`, allowing the JVM to unmount virtual threads during blocking calls.

---

### 4. Unbounded Concurrency in Project Scans *(High Priority)*

**Problem**: In `FileScannerService.java`, the `scanProject()` and `scanChangedFiles()` methods walk the file tree and submit all discovered files concurrently to the virtual-thread-backed `applicationTaskExecutor` via `CompletableFuture.runAsync()`.
- **DB Connection Exhaustion**: Spawning thousands of concurrent database transactions will instantly saturate the HikariCP connection pool (limited to 50 connections), causing connection timeouts (`SQLTransientConnectionException`).
- **Memory Pressure**: Reading and parsing thousands of source files concurrently using `JavaParser` will result in massive heap allocation and GC thrashing (or OutOfMemoryError).
- **Lucene Contention**: Writing thousands of updates to the same `IndexWriter` concurrently creates severe internal write lock contention.

**Fix**: Introduce a `Semaphore` (e.g., limit concurrency to 12 or 16) in `FileScannerService` to throttle the concurrent execution of file indexing.

---

### 5. Missing Timeout Configuration on `OllamaClient` *(Low Priority)*

**Problem**: `OllamaProperties` contains `timeoutSeconds`, but `OllamaClient` builds its `RestClient` without setting a request factory or configuring connect/read timeouts. If the local Ollama instance hangs, calls to `/chat/completions` will block indefinitely.

**Fix**: Configure a `SimpleClientHttpRequestFactory` on the `RestClient.builder()` in `OllamaClient.init()`, matching the pattern used in `LlmClient.java`.

---

## Priority Summary

| # | Issue | Area | Priority |
|---|-------|------|----------|
| 1 | In-memory `sessionStore` (not restart-safe) | Reliability | 🔴 High |
| 2 | Zero test coverage | Quality | 🔴 High |
| 3 | Virtual Thread Carrier Pinning (synchronized blocks wrapping blocking I/O) | Concurrency | 🔴 High |
| 4 | Unbounded Concurrency in Project Scans (connection pool/OOM risks) | Performance | 🔴 High |
| 5 | Missing Timeout Configuration on `OllamaClient` | Resiliency | 🟢 Low |

---

## Overall Assessment

The project is **architecturally sound** with several well-made engineering decisions. The remaining concerns are **operability** (in-memory sessions), **quality assurance** (zero tests), and **performance/concurrency optimizations** (preventing carrier thread pinning, throttling project scanner concurrency, and establishing client HTTP timeouts). Addressing these items will ensure the system scales efficiently under concurrent AI agent workflows.
