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

### 1. `CodebaseController` — God Class *(High Priority)*

**Problem**: 14 injected dependencies in a single controller. Handles file reading, symbol search, batch fetching, topology, reconciliation, summarization, endpoint analysis, and git integration — a clear Single Responsibility violation.

**Fix**: Extract into focused controllers or introduce a `CodebaseQueryFacade` service:
- `CodebaseReadController` — file / search / symbols / suggest / topology
- `CodebaseOpsController` — scan / reconcile / batch
- `SymbolController` — symbol detail / hierarchy

---

### 2. In-Memory `sessionStore` in `McpController` *(High Priority)*

**Problem**: Sessions are stored in a bounded `LinkedHashMap` — lost on restart, not shareable across instances, no durable TTL enforcement.

```java
// McpController.java ~L52
private final Map<String, Session> sessionStore; // ← in-memory only
```

**Fix**: Persist sessions to the DB (the `BrowserSession` entity is an existing pattern to follow) or use a Redis store. At minimum, document the single-instance constraint explicitly in the README.

---

### 3. Zero Test Coverage *(High Priority)*

**Problem**: `src/test/resources` is empty; no test classes exist anywhere. Core services like `FileIndexerService` and `LuceneIndexService` carry significant logic with no safety net.

**Fix**: Add at minimum:

| Test Class | Scope |
|---|---|
| `FileIndexerServiceTest` | Parse a sample `.java` file, assert correct symbols extracted |
| `LuceneIndexServiceTest` | Index + search round-trip with real on-disk index |
| `ProjectServiceTest` | Project create/delete lifecycle via `@DataJpaTest` |
| `CodebaseControllerTest` | `@WebMvcTest` for each `X-Op` value |

---

### 4. Raw `new ObjectMapper()` — Not Spring-Managed *(Medium Priority)*

**Problem**:
```java
// McpController.java ~L50
private static final ObjectMapper objectMapper = new ObjectMapper(); // ← raw new
```
Bypasses Spring's configured `ObjectMapper` (custom serializers, date formats, `JavaTimeModule`, etc.). Deserialization via `convertBody` will silently produce wrong results for `LocalDate`, `Instant`, and similar types.

**Fix**: Inject `ObjectMapper` via constructor DI, letting Spring provide the fully-configured instance.

---

### 5. Repository Access Leaking Into `CodebaseController` *(Medium Priority)*

**Problem**: `CodebaseController` directly injects `FileMetadataRepository`, `SymbolRepository`, and `SymbolCallRepository`. This bypasses the service layer and makes the controller impossible to unit-test without a full Spring context.

**Fix**: Move all repository calls into services. The controller should only call services.

---

### 6. `LuceneIndexService` — 6 Overloaded `searchContent` Methods *(Medium Priority)*

**Problem**: Six overloaded variants with various combinations of `type`, `site`, `filePaths`, `limit`, and `offset`. Callers can silently pick the wrong overload.

**Fix**: Consolidate with an options/builder pattern:
```java
SearchOptions opts = SearchOptions.builder()
    .query("foo")
    .type("java")
    .limit(20)
    .offset(0)
    .build();
List<ContentSearchResult> results = luceneIndexService.searchContent(projectId, opts);
```

---

### 7. `SkillService` Dependency in `FileIndexerService` *(Medium Priority)*

**Problem**: `FileIndexerService` injects `SkillService` — indexing logic shouldn't depend on agent-layer skill management. Creates tight coupling and circular dependency risk.

**Fix**: Remove `SkillService` from `FileIndexerService`. If skill context is needed during indexing, pass it as a method parameter from the calling service.

---

### 8. Untyped `Object` Return Types on Public Endpoints *(Medium Priority)*

**Problem**: `codebaseRead` and `codebaseOp` both return `Object` — no contract, misleading OpenAPI docs, fragile client code.

**Fix**: Use a sealed interface or discriminated union DTO:
```java
sealed interface CodebaseResult
    permits FileContext, SearchResult, SymbolList, TopologyResult, BatchResult {}
```
Or at minimum, typed response wrappers per `X-Op` value.

---

### 9. Missing `@Transactional` on `reindexProject` *(Medium Priority)*

**Problem**: `ProjectService.reindexProject` deletes and rebuilds symbol data without `@Transactional`. A mid-operation failure leaves the index in a corrupt partial state.

**Fix**: Add `@Transactional` (or `@Transactional(rollbackFor = Exception.class)`).

---

### 10. Fully-Qualified Type Names Instead of Imports *(Low Priority)*

**Problem**: Multiple fields use FQNs instead of proper imports:
```java
private final com.mcp.repository.SymbolCallRepository symbolCallRepository;
private final com.mcp.service.ProjectService projectService;
private final org.springframework.core.io.Resource jcbSkillResource;
```
Indicates leftover auto-generation or incomplete refactoring.

**Fix**: Add `import` statements; use short type names throughout.

---

## Priority Summary

| # | Issue | Area | Priority |
|---|-------|------|----------|
| 1 | `CodebaseController` God Class | Architecture | 🔴 High |
| 2 | In-memory `sessionStore` (not restart-safe) | Reliability | 🔴 High |
| 3 | Zero test coverage | Quality | 🔴 High |
| 4 | Raw `new ObjectMapper()` bypass | Bug Risk | 🟡 Medium |
| 5 | Repository access in controllers | Layering | 🟡 Medium |
| 6 | 6 overloaded `searchContent` methods | Maintainability | 🟡 Medium |
| 7 | `SkillService` in `FileIndexerService` | Coupling | 🟡 Medium |
| 8 | Untyped `Object` returns on endpoints | API Contract | 🟡 Medium |
| 9 | Missing `@Transactional` on `reindexProject` | Data Integrity | 🟡 Medium |
| 10 | FQN references instead of imports | Cosmetic | 🟢 Low |

---

## Overall Assessment

The project is **architecturally sound** with several well-made engineering decisions (dual-index, virtual threads, ETag caching, gitignore integration, skill system). The primary concerns are **operability** (in-memory sessions, missing `@Transactional`), **maintainability** (controller bloat, overloaded APIs), and **quality assurance** (zero tests). Addressing items 1–3 should be the immediate focus before this is used in a production agent workflow.
