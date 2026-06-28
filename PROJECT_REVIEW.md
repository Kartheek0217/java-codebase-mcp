# Project Review — `java-codebase-mcp`

> **Reviewed**: 2026-06-28  
> **Stats**: 117 source files · 1038 symbols · Spring Boot 4.0 · JDK 25  
> **Session**: `aafc93e5-28f1-4167-9ee6-5b6c187c7ea2`

---

## Architecture Overview

```
Controllers (REST/MCP)
    ├── CodebaseController   → file indexing, search, symbols, topology, batch
    ├── TaskManagerController→ project tasks, rules
    ├── SessionController    → sessions
    ├── SkillController      → skills
    ├── AgentController      → background agent tasks, SSE streaming, sync actions
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
    ├── AgentClient                  → HTTP client
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

### 1. Incomplete Path Traversal Protections *(High Priority)*
**Problem**: Naive check `path.toString().contains("..")` or string checks in [SkillController](file:///src/main/java/com/mcp/controller/SkillController.java) and [CodebaseController](file:///src/main/java/com/mcp/controller/CodebaseController.java) are bypassable. Standard normalization resolves dots prior to some validations, allowing directory escape.
**Fix**: Resolve paths to absolute representations and strictly verify that they start with the target safe base directory:
```java
Path base = Paths.get("/app/skills").toAbsolutePath().normalize();
Path target = base.resolve(filePath).normalize();
if (!target.startsWith(base)) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
}
```

### 2. SSRF / Unsafe URL Input *(High Priority)*
**Problem**: [SkillController](file:///src/main/java/com/mcp/controller/SkillController.java) and [BrowserController](file:///src/main/java/com/mcp/controller/BrowserController.java) accept query URLs only checked for scheme prefix. Loops, private IP blocks, or protocols like `file://` / `javascript:` are not restricted, introducing Server-Side Request Forgery risks.
**Fix**: Restrict URL schemas via `java.net.URI` validation and block loopback/private subnet IPs.

### 3. Controller Parameter Validation Misuse *(Medium Priority)*
**Problem**: Request parameters and header variables are annotated with `@NotBlank`, `@NotNull`, or `@Pattern` without activating method-level validation. Thus, Spring MVC silently ignores these validations.
**Fix**: Ensure `@Validated` is correctly activated and annotated on classes, and validation DTOs cascade using `@Valid`.

### 4. Overly Broad Exception Handling & Weak Casts *(Medium Priority)*
**Problem**: Broad catch clauses like `catch (Exception e)` or `@ExceptionHandler(IOException.class)` swallow critical errors (OOM, database crashes) and throw misleading HTTP 404/500 responses.
Furthermore, unsafe casts like `(Project) projectService.createProjectAndIndex(...)` (which returned a `Map` instead of `Project`) were present, crashing the endpoint.
**Fix**: Return typed DTOs from services to eliminate ClassCastException issues, and write narrow exception handlers via a global `@ControllerAdvice`.

### 5. RPC-over-POST REST Semantics Violations *(Medium Priority)*
**Problem**: [BrowserController](file:///src/main/java/com/mcp/controller/BrowserController.java) routes browser commands (click, navigate, screenshot) on a single `POST /session/{id}` endpoint using an custom `X-Action` header. This bypasses caching, preflights, and standards.
**Fix**: Deconstruct into dedicated resource endpoints (e.g. `POST /session/{sessionId}/navigate`).

### 6. Duplicated SSE Streaming Boilerplate *(Low Priority)*
**Problem**: [AgentController](file:///src/main/java/com/mcp/controller/AgentController.java) SSE streaming endpoints (`/explain-file`, `/ask`, `/code-review`, etc.) replicate identical parameter parsing and stream-negotiation boilerplate.
**Fix**: Consolidate stream routing or delegate to a unified handler passing an enum-typed action parameter.

### 7. Pagination & Resource Sweeping *(Medium Priority)*
**Problem**:
- Unbounded GET queries on `/skills` or `/tasks` do not support pagination, posing OOM risks.
- Headless browser sessions created by `BrowserController` persist indefinitely without an idle TTL cleanup, causing memory leaks.
**Fix**: Introduce pagination using `Pageable` parameters and enforce idle browser session cleanup using a background `@Scheduled` sweeper task.

---

## Priority Summary

| # | Issue | Area | Priority |
|---|-------|------|----------|
| 1 | Incomplete Path Traversal guards in `Skill` & `Codebase` Controllers | Security | 🔴 High |
| 2 | SSRF vulnerabilities via unsafe URL inputs | Security | 🔴 High |
| 3 | Ineffective Spring method validation annotations | Correctness | 🟠 Medium |
| 4 | Overly broad Exception Handlers and ClassCastExceptions | Reliability | 🟠 Medium |
| 5 | Lack of pagination and idle browser session cleanup | Performance | 🟠 Medium |
| 6 | Duplicated SSE stream endpoints | Maintainability | 🟢 Low |

---

## Overall Assessment

While the backend exhibits **excellent architecture layering**, **virtual-thread usage**, and a **precise dual indexing pipeline**, the controller layer carries critical security concerns (path traversal, SSRF) and validation bugs. Remediation of path resolution safety, parameter validation bindings, and explicit controller contract definitions will make the system highly robust and ready to host secure, high-concurrency AI workflows.
