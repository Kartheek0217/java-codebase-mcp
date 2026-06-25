# Project Review — `java-codebase-mcp`

> **Reviewed**: 2026-06-11  
> **Stats**: 153 files · 906 symbols · Spring Boot 4.0 · JDK 25  
> **Session**: `aafc93e5-28f1-4167-9ee6-5b6c187c7ea2`

---

## Architecture Overview

```
Controllers (REST/MCP)
    ├── CodebaseController   → file indexing, search, symbols, topology, batch
    ├── TaskManagerController→ tasks, rules
    ├── SessionController    → sessions
    ├── SkillController      → skills
    ├── AgentController        → AGENT actions (explain, review, refactor, test-gen)
    ├── ProjectController    → CRUD, reindex, git-status, stage/commit
    ├── BrowserController    → Playwright headless browser sessions
    └── SystemController     → health, AGENT status

Services (Core)
    ├── FileIndexerService          → JavaParser AST + multi-language regex extraction
    ├── LuceneIndexService          → per-project Lucene index (SearcherManager + IndexWriter)
    ├── FileDataPersistenceService  → symbol/call persistence
    ├── FileScannerService          → .gitignore-aware filesystem scan
    ├── ReconciliationService       → stale index cleanup
    ├── ProjectService              → project lifecycle + async post-commit indexing
    └── ContextMemoryService        → Caffeine-backed session memory

Services (AGENT)
    ├── AgentService                  → facade
    ├── AgentClient / OllamaClient    → HTTP clients
    ├── AgentPromptBuilder            → prompt construction
    ├── AgentStreamingService         → SSE streaming
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
`codebaseRead` supports `If-None-Match` + checksum-based ETags — avoids re-sending unchanged file content to AGENT tools. Smart token optimization for agent workflows.

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

### 1. In-Memory `sessionStore` in `SessionController` *(High Priority)*

**Problem**: Sessions are stored in a bounded `LinkedHashMap` — lost on restart, not shareable across instances, no durable TTL enforcement.

```java
// SessionController.java
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

## Priority Summary

| # | Issue | Area | Priority |
|---|-------|------|----------|
| 1 | In-memory `sessionStore` (not restart-safe) | Reliability | 🔴 High |
| 2 | Zero test coverage | Quality | 🔴 High |

---

## Overall Assessment

The project is **architecturally sound** with several well-made engineering decisions. The remaining concerns are **operability** (in-memory sessions) and **quality assurance** (zero tests). Addressing these items will ensure the system scales efficiently and is robust under concurrent AI agent workflows.
