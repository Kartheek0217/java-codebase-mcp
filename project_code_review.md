# java-codebase-mcp — Project Code Review

> **Reviewed:** All 6 controllers · 12+ services · All entities · `pom.xml`  
> **Reviewed on:** 2026-06-09  
> **Legend:** 🔴 Bug/Risk · 🟡 Design Smell · 🔵 Nit/Polish · ✅ Good

---

## Table of Contents

1. [Security](#1-security)
2. [Concurrency & Thread Safety](#2-concurrency--thread-safety)
3. [Data Access & JPA](#3-data-access--jpa)
4. [Error Handling & Resilience](#4-error-handling--resilience)
5. [Architecture & Design](#5-architecture--design)
6. [Lucene Index](#6-lucene-index)
7. [Infrastructure & Configuration](#7-infrastructure--configuration)
8. [What's Done Well](#8-whats-done-well-)
9. [Priority Fix Order](#9-priority-fix-order)

---

## 1. Security

### S1 🔴 `LlmClient` — `ObjectMapper` not thread-safe
**File:** `LlmClient.java` L104  
**Problem:** `new ObjectMapper()` created as an instance field — not thread-safe under concurrent SSE streams that call `parseDeltaContent()` in parallel.

```java
// ❌ Current — instance field, shared across concurrent SSE calls
private final ObjectMapper objectMapper = new ObjectMapper();

// ✅ Fix — inject Spring's shared singleton bean
@Autowired private ObjectMapper objectMapper;
// OR make it static final (ObjectMapper is thread-safe when fully configured at init time)
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
```

---

### S2 🟡 `LlmController` — Internal action names leaked in 400 errors
**File:** `LlmController.java` L69  
**Problem:** Unknown `X-Action` values result in a 400 that echoes the raw action name back to the client, leaking internal routing vocabulary.

```java
// ❌ Current
throw new ResponseStatusException(BAD_REQUEST, "Unknown LLM action: " + action);

// ✅ Fix — sanitise error body in @RestControllerAdvice; never echo raw input
throw new ResponseStatusException(BAD_REQUEST,
    "Unknown action. Allowed: explain-symbol, explain-file, ask, code-review, ...");
```

---

### S3 🟡 `LlmService` — Relative fallback path for skill file
**File:** `LlmService.java` L71–76  
**Problem:** Fallback path `src/main/resources/skills/global/jcb/SKILL.md` is resolved from JVM working directory — reads from wherever the JAR was launched, not from the classpath.

```java
// ❌ Current
Path path = Paths.get("src/main/resources/skills/global/jcb/SKILL.md");

// ✅ Fix — classpath only, no filesystem fallback
@Value("classpath:skills/global/jcb/SKILL.md")
private Resource jcbSkillResource;
// Remove the filesystem fallback block entirely
```

---

### S4 🟡 `GitInfoService` — Commit message not sanitised
**File:** `GitInfoService.java` — `commit()` method  
**Problem:** Commit message is taken directly from a query param with no length cap or newline stripping — could inject malformed git trailers.

```java
// ✅ Fix — add guard before passing to JGit
if (message == null || message.isBlank())
    throw new ResponseStatusException(BAD_REQUEST, "Commit message required");
String sanitised = message.strip().replaceAll("[\r\n]", " ");
if (sanitised.length() > 500)
    throw new ResponseStatusException(BAD_REQUEST, "Commit message exceeds 500 chars");
```

---

### S5 🔵 `ProjectController` — `rootPath` symlink traversal
**File:** `ProjectController.java` — `createProject()`  
**Problem:** Validates existence and readability, but doesn't prevent a symlink pointing to `/etc/passwd` or sensitive system paths.

```java
// ✅ Fix — resolve real path and optionally restrict to a safe base
Path realPath = path.toRealPath(); // resolves symlinks
// Optionally: assert realPath starts with allowedBase
```

---

## 2. Concurrency & Thread Safety

### C1 🔴 `GitInfoService` — Repository cache race condition
**File:** `GitInfoService.java` L89  
**Problem:** `getRepository()` is `synchronized` on `this`, but callers (`getProjectStatus`, `stageFiles`, `commit`) invoke it without holding the lock. Two threads can observe the same cache miss simultaneously and both open a new `Repository`.

```java
// ❌ Current — coarse synchronized that doesn't prevent races in callers
private synchronized Repository getRepository(Long projectId) { ... }

// ✅ Fix — use computeIfAbsent with a non-blocking factory
private final ConcurrentHashMap<Long, Repository> repositoryCache = new ConcurrentHashMap<>();

private Repository getRepository(Long projectId) throws IOException {
    Repository existing = repositoryCache.get(projectId);
    if (existing != null) return existing;
    // Use a per-project lock to avoid double-open
    synchronized (("repo-lock-" + projectId).intern()) {
        return repositoryCache.computeIfAbsent(projectId, this::openRepository);
    }
}
```

---

### C2 🔴 `LuceneIndexService` — Commit vs. close race
**File:** `LuceneIndexService.java` L90, L389  
**Problem:** `scheduleCommits()` iterates `writers` and calls `writer.commit()`. Concurrently, `deleteIndex()` calls `writers.remove(projectId)` and `writer.close()`. A `commit()` on a closed writer throws `AlreadyClosedException`.

```java
// ✅ Fix — guard in scheduleCommits with a null/open check
writers.forEach((projectId, writer) -> {
    try {
        if (writer.isOpen() && writer.hasUncommittedChanges()) {
            writer.commit();
        }
    } catch (AlreadyClosedException ignored) {
        // writer was concurrently deleted — safe to skip
    } catch (IOException e) { ... }
});
```

---

### C3 🟡 `McpController` — DB call inside synchronized map callback
**File:** `McpController.java` L61–70  
**Problem:** `removeEldestEntry()` calls `contextMemoryService.clearSession()` which hits the database — inside a `synchronized(LinkedHashMap)` callback. This risks lock inversion with JPA/Hibernate locks.

```java
// ✅ Fix — collect expired keys, release the lock, then clear
// Move DB cleanup entirely into the @Scheduled cleanupExpiredSessions() method
@Scheduled(fixedDelay = 3_600_000)
public void cleanupExpiredSessions() {
    long now = System.currentTimeMillis();
    List<String> expired;
    synchronized (sessionStore) {
        expired = sessionStore.entrySet().stream()
            .filter(e -> now - e.getValue().createdAt() > 3_600_000L)
            .map(Map.Entry::getKey).toList();
        expired.forEach(sessionStore::remove);
    }
    expired.forEach(contextMemoryService::clearSession); // outside lock
}
```

---

### C4 🟡 `FileScannerService` — Blocking join on platform thread path
**File:** `FileScannerService.java` L192  
**Problem:** `scanProject()` calls `.allOf(...).join()` which blocks the calling thread. When invoked from `ProjectService.createProject()` → `afterCommit()` hook (runs on a platform thread), this blocks a platform thread for the full scan duration.

```java
// ✅ Fix — make scanProject return CompletableFuture<Void>
public CompletableFuture<Void> scanProject(Long projectId, Long taskId) {
    return CompletableFuture.runAsync(() -> { /* scan logic */ }, applicationTaskExecutor);
}
```

---

### C5 🔵 `FileIndexerService` — `self` field not `volatile`
**File:** `FileIndexerService.java` L68  
**Problem:** `self` is written by Spring's proxy injection from one thread and read by indexing threads — without `volatile`, JVM can serve stale null.

```java
// ❌ Current
private FileIndexerService self;

// ✅ Fix
private volatile FileIndexerService self;
```

---

## 3. Data Access & JPA

### D1 🔴 N+1 Queries in `getAllProjectSummaries()`
**File:** `ProjectService.java` L147–155  
**Problem:** For N projects, fires `2 × N` SQL count queries inside a stream loop.

```java
// ❌ Current — 2N SQL calls
projectRepository.findAll().stream().map(p -> {
    map.put("fileCount", fileMetadataRepository.countByProjectId(p.getId()));
    map.put("symbolCount", symbolRepository.countByProjectId(p.getId()));
    ...
})

// ✅ Fix — single projection query
// In ProjectRepository:
@Query("""
    SELECT p.id, p.name, p.rootPath,
           COUNT(DISTINCT f.filePath), COUNT(DISTINCT s.id)
    FROM Project p
    LEFT JOIN FileMetadata f ON f.projectId = p.id
    LEFT JOIN Symbol s ON s.projectId = p.id
    GROUP BY p.id, p.name, p.rootPath
    """)
List<Object[]> findAllProjectSummaries();
```

---

### D2 🔴 Redundant `findById` after `save` in `createTask()`
**File:** `TaskService.java` L65  
**Problem:** Calls `taskRepository.findById(savedTask.getId())` immediately after `taskRepository.save(task)` within the same transaction — the managed entity returned by `save()` is already fully populated.

```java
// ❌ Current — unnecessary extra SELECT
return toDTO(taskRepository.findById(savedTask.getId()).orElseThrow());

// ✅ Fix
return toDTO(savedTask); // already the managed, persisted entity
```

---

### D3 🟡 Raw `RuntimeException` for not-found entities
**File:** `ProjectService.java` L102, `TaskService.java`, `GitInfoService.java`  
**Problem:** Throws `RuntimeException("Project not found")` — not caught by `@RestControllerAdvice`, results in 500 with a stack trace exposed to clients.

```java
// ❌ Current
throw new RuntimeException("Project not found");

// ✅ Fix — use typed exception with HTTP mapping
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id);
// OR create domain exceptions:
throw new ProjectNotFoundException(id); // mapped in GlobalExceptionHandler
```

---

### D4 🟡 Double `findById` for `FileMetadata` in indexer
**File:** `FileIndexerService.java` L114, L210  
**Problem:** `fileMetadataRepository.findById()` is called in `indexFile()` and then again inside `saveFileData()` if `existingMetadata == null` — duplicate DB round-trip.

```java
// ✅ Fix — always pass through the fetched metadata; remove the redundant fetch in saveFileData
// In indexFile(): metadata is already fetched → always pass non-null
// In saveFileData(): remove the fallback findById call
```

---

### D5 🟡 Entities use verbose accessor pattern
**Files:** `Symbol.java`, `SymbolCall.java`, `Skill.java`, `BrowserSession.java`  
**Problem:** 12–20 raw getter/setter methods per entity with no logic — pure boilerplate that obscures the data model.

```java
// ✅ Fix — use Lombok (already on Spring Boot parent BOM)
@Entity
@Table(name = "symbols")
@Getter @Setter
@NoArgsConstructor
public class Symbol {
    @Id @GeneratedValue(...)
    private Long id;
    // ... fields only
}
```

---

### D6 🔵 Missing DB indexes on hot query columns
**File:** `Symbol.java`, `SymbolCall.java`  
**Problem:** No `@Index` on the two most queried column combinations.

```java
// ✅ Fix — add to @Table annotation
@Table(name = "symbols", indexes = {
    @Index(name = "idx_symbol_project_name", columnList = "project_id, name"),
    @Index(name = "idx_symbol_project_path", columnList = "project_id, file_path")
})

@Table(name = "symbol_calls", indexes = {
    @Index(name = "idx_call_caller", columnList = "caller_id"),
    @Index(name = "idx_call_project_callee", columnList = "project_id, callee_name")
})
```

---

### D7 🔵 Missing index on `SymbolCall.caller_id`
**File:** `SymbolCall.java`  
See D6 above — `findByCallerId()` runs a full table scan without an index.

---

## 4. Error Handling & Resilience

### E1 🔴 `catch (Throwable)` silently swallows all errors
**File:** `FileIndexerService.java` L160  
**Problem:** `catch (Throwable t)` swallows `OutOfMemoryError`, `StackOverflowError`, and all runtime exceptions. File appears indexed but no metadata is updated — silent data corruption.

```java
// ❌ Current
} catch (Throwable t) {
    logger.error("Critical error indexing file: {}", path, t);
}

// ✅ Fix — catch Exception only; let Errors propagate
} catch (Exception e) {
    logger.error("Failed to index file {} in project {}: {}", path, projectId, e.getMessage(), e);
    // Optionally mark file as FAILED in metadata
}
```

---

### E2 🔴 Lucene errors indistinguishable from empty results
**File:** `LuceneIndexService.java` L315  
**Problem:** `catch (Exception e)` returns an empty list — a `CorruptIndexException` looks identical to "no results found" to the caller.

```java
// ✅ Fix — distinguish error from empty
} catch (AlreadyClosedException | CorruptIndexException e) {
    logger.error("Index unavailable for project {}", projectId, e);
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Search index temporarily unavailable");
} catch (Exception e) {
    logger.error("Search failed for project {}: {}", projectId, queryStr, e);
    return List.of(); // safe fallback for transient query errors
}
```

---

### E3 🟡 `GitInfoService` throws `RuntimeException` → 500 with stack trace
**File:** `GitInfoService.java` L155  
See D3 — wrap in `ResponseStatusException(INTERNAL_SERVER_ERROR)` with a sanitised message.

---

### E4 🟡 `TaskStatus.valueOf()` can throw uncaught `IllegalArgumentException`
**File:** `McpController.java` — `taskOp()` case `update-step`  
**Problem:** If `status` is an invalid string, `valueOf()` throws `IllegalArgumentException` which bubbles up as 500.

```java
// ✅ Fix — catch and return 400
try {
    TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
} catch (IllegalArgumentException e) {
    throw new ResponseStatusException(BAD_REQUEST,
        "Invalid status '" + status + "'. Allowed: " + Arrays.toString(TaskStatus.values()));
}
```

---

### E5 🔵 Raw exception in SSE `completeWithError`
**File:** `LlmService.java` L464  
**Problem:** `emitter.completeWithError(ex)` sends a Java exception object to the SSE client — clients receive a raw serialised exception.

```java
// ✅ Fix — emit a structured error event before completing
emitter.send(SseEmitter.event().name("error")
    .data("{\"error\":\"LLM action failed: " + ex.getMessage() + "\"}"));
emitter.complete();
```

---

## 5. Architecture & Design

### A1 🔴 `getFileContext()` returns `Object` with 3 different types
**File:** `CodebaseController.java` — `getFileContext()` helper  
**Problem:** Returns either `ResponseEntity<ContextDTO>`, `ResponseEntity<String>` (markdown), or `ResponseEntity<Void>` (304). Callers cast blindly — a missed type causes a `ClassCastException` at runtime.

```java
// ✅ Fix — create a FileContextService with a proper sealed return type
// or always return ResponseEntity<Object> with consistent body structure
// and document the discriminator field (e.g. "type": "context" | "markdown")
```

---

### A2 🟡 Self-proxy injection anti-pattern in `FileIndexerService`
**File:** `FileIndexerService.java` L81–84  
**Problem:** `@Autowired setSelf(@Lazy FileIndexerService self)` is a workaround for calling `@Transactional` methods on `this`. This is an anti-pattern — it breaks testability and Spring's AOP proxy model.

```java
// ✅ Fix — extract a new service bean
@Service
public class FileDataPersistenceService {
    @Transactional
    public void saveFileData(...) { ... }
}
// Inject FileDataPersistenceService into FileIndexerService — no self-proxy needed
```

---

### A3 🟡 `LlmService` violates Single Responsibility (628 lines)
**File:** `LlmService.java`  
**Problem:** Handles message building, prompt engineering, web-search orchestration, file reading, and SSE streaming in one class.

```
✅ Suggested split:
  LlmPromptBuilder       — builds List<Message> per action type
  LlmStreamingService    — owns SseEmitter creation + streamLlmAction()
  WebSearchOrchestrator  — owns browser session lifecycle + DuckDuckGo query
  LlmService             — thin facade that delegates to the above
```

---

### A4 🟡 `McpController.convertBody()` creates new `ObjectMapper` per call
**File:** `McpController.java` — `convertBody()`  
**Problem:** `new ObjectMapper()` is instantiated on every call to deserialise a task body — expensive and bypasses Spring's configured `ObjectMapper` (e.g. `JavaTimeModule`).

```java
// ✅ Fix — inject the Spring-managed ObjectMapper
private final ObjectMapper objectMapper;

public McpController(..., ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
}
```

---

### A5 🟡 `reindexProject()` incorrectly annotated `@Transactional(readOnly=true)`
**File:** `ProjectService.java` L105  
**Problem:** The method only reads one entity and then fires async background work — the `@Transactional(readOnly=true)` annotation implies DB write safety but is misleading.

```java
// ✅ Fix — remove @Transactional entirely; it adds no value here
public void reindexProject(Long id) { ... }
```

---

### A6 🔵 `codebaseRead()` has 11 `@RequestParam` for 11 operations
**File:** `CodebaseController.java`  
**Problem:** Every new X-Op operation may add yet another optional param — the method signature already has 10 params and grows unbounded.

```java
// ✅ Consider a CodebaseQuery record for optional fields
public record CodebaseQuery(
    String filePath, String query, String sessionId,
    String format, String type, int limit,
    String controllerName, String methodName
) {}
// Bind from request params using @ModelAttribute CodebaseQuery q
```

---

## 6. Lucene Index

### L1 🔴 `parseSnippets()` — condition is logically backwards
**File:** `LuceneIndexService.java` L342  
**Problem:** The comment says it was "fixed" — but the fix is wrong. `if (fileContent != null && fileContent.isEmpty())` nulls out *empty* content but leaves `null` content unchanged — the opposite of the intent.

```java
// ❌ Current — wrong condition
if (fileContent != null && fileContent.isEmpty()) {
    fileContent = null;
}

// ✅ Fix — null out both null and empty uniformly
if (fileContent == null || fileContent.isEmpty()) {
    fileContent = null;
}
```

---

### L2 🟡 Lucene index directory is not configurable
**File:** `LuceneIndexService.java` L49  
**Problem:** `BASE_INDEX_DIR` resolved from `user.dir` at class-load time — differs between `mvn spring-boot:run` (project root) and a deployed JAR.

```java
// ❌ Current
private static final String BASE_INDEX_DIR =
    Paths.get(System.getProperty("user.dir"), "data", "indices").toAbsolutePath().toString();

// ✅ Fix — inject via @Value
@Value("${lucene.indexDir:${user.dir}/data/indices}")
private String baseIndexDir;
```

---

### L3 🟡 Full file content stored in Lucene index — bloats index size
**File:** `LuceneIndexService.java` L171  
**Problem:** `doc.add(new TextField("content", content, Field.Store.YES))` stores the full source file in the index. For a 1,000-file project this duplicates all code on disk.

```java
// ✅ Fix — store content in the index for highlighting, but add a size gate
// OR reconstruct highlights from the filesystem on demand (don't Store.YES)
doc.add(new TextField("content", content, Field.Store.NO)); // don't store full content
// Highlights are re-extracted by fetching the file from disk at search time
```

---

### L4 🔵 `SEARCH_TIMEOUT_MS` hardcoded
**File:** `LuceneIndexService.java` L51  
**Problem:** `private static final long SEARCH_TIMEOUT_MS = 2000` — cannot be tuned without a rebuild.

```java
// ✅ Fix — add to LuceneProperties
@Value("${lucene.searchTimeoutMs:2000}")
private long searchTimeoutMs;
```

---

## 7. Infrastructure & Configuration

### I1 🔴 H2 in-memory database — all data lost on restart
**File:** `pom.xml` L51  
**Problem:** `h2` with `scope=runtime` defaults to in-memory mode — every restart wipes all projects, symbols, tasks, and rules.

```properties
# ✅ Fix — application.properties for dev persistence
spring.datasource.url=jdbc:h2:file:./data/db;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver

# For production — switch to PostgreSQL profile
spring.datasource.url=jdbc:postgresql://localhost:5432/jcb
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}
```

---

### I2 🟡 JVM args hardcoded in `pom.xml`
**File:** `pom.xml` L149  
**Problem:** `-Xms512m -Xmx2g` inside the Maven plugin override any runtime JVM configuration and can't be adjusted without rebuilding.

```xml
<!-- ✅ Fix — remove from pom.xml; use env var instead -->
<!-- In pom.xml: remove <jvmArguments> -->
<!-- At runtime: -->
<!-- export JAVA_TOOL_OPTIONS="-Xms512m -Xmx2g -XX:+UseG1GC" -->
<!-- ./mvnw spring-boot:run -->
```

---

### I3 🟡 Spring Boot 4.0.6 ecosystem gap not documented
**File:** `pom.xml` L8  
**Problem:** Spring Boot 4.x requires JDK 25+ and Jakarta EE 11. Many ecosystem libraries have not published compatible versions yet. No `README` callout exists.

```markdown
<!-- ✅ Fix — add to README.md -->
## Requirements
- JDK 25 or later (Spring Boot 4.x baseline)
- Maven 3.9+
- Note: Spring Boot 4.x is Jakarta EE 11 — ensure all third-party libs support it
```

---

### I4 🟡 `GitInfoService` repository cache holds OS file handles indefinitely
**File:** `GitInfoService.java` L33  
**Problem:** `repositoryCache` holds JGit `Repository` objects (each owns OS file descriptors) until the idle cleanup fires at 30-minute intervals — a large number of projects can exhaust file descriptors.

```java
// ✅ Fix — use Caffeine with weak values or a shorter idle eviction
Cache<Long, Repository> repositoryCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(10))
    .removalListener((id, repo, cause) -> repo.close())
    .build();
```

---

### I5 🔵 Zero test coverage
**Files:** No `src/test` directory exists  
**Problem:** No unit or integration tests — any refactoring is blind.

```xml
<!-- ✅ Add to pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

```
Minimum test targets:
  FileIndexerServiceTest  — symbol extraction from Java source
  LuceneIndexServiceTest  — index/search/delete round-trip
  ProjectServiceTest      — createProject, deleteProject
  @SpringBootTest         — application context loads without errors
```

---

## 8. What's Done Well ✅

| Area | Detail |
|---|---|
| **Virtual Threads** | `BATCH_EXECUTOR`, `FileScannerService`, `LlmService.streamResponse()` all correctly use VTs — no carrier thread blocking |
| **Caffeine Cache** | Bounded `Cache<String, List<Symbol>>` for symbol lookup — prevents unbounded heap growth |
| **ETag Caching** | `If-None-Match` + SHA-256 checksum for file context — avoids redundant LLM token spend |
| **Lucene NRT** | `SearcherManager` + `maybeRefresh()` gives near-real-time search visibility without index close/reopen |
| **Bulk Mode** | `setBulkMode(true/false)` defers Lucene commits during initial scan — correct large-write optimisation |
| **`.gitignore` Parsing** | `IgnoreNode` from JGit correctly skips gitignored paths during scan |
| **JGit Idle Eviction** | `@Scheduled` cleanup of idle `Repository` objects prevents OS file-handle leaks |
| **`AFTER_COMMIT` Event** | `afterCommit()` pattern in `createProject()` ensures background scan only starts if the DB transaction succeeds |
| **Sequence Generators** | All entities use `allocationSize = 50` — avoids one `SELECT nextval` per `INSERT` |
| **`BLEEDING_EDGE` Parser** | JavaParser configured for JDK 25 syntax — future-proof for unnamed variables and pattern matching |
| **Constructor Injection** | All Spring beans use constructor injection — no field `@Autowired` (except the self-proxy workaround) |
| **Lucene `TermInSetQuery`** | `search-changed` uses `TermInSetQuery` for efficient path-set filtering instead of N individual term queries |

---

## 9. Priority Fix Order

### 🔴 Immediate — Fix Before Next Release

| ID | File | Impact |
|---|---|---|
| **I1** | `pom.xml` | H2 persistence — all indexed data lost on restart |
| **D1** | `ProjectService` | N+1 queries — degrades with every project added |
| **L1** | `LuceneIndexService` | parseSnippets bug — line numbers always return 0 |
| **C1** | `GitInfoService` | Repository cache race condition |
| **S1** | `LlmClient` | ObjectMapper thread-safety under concurrent SSE |

### 🟡 Next Sprint — Design Improvements

| ID | File | Impact |
|---|---|---|
| **A2** | `FileIndexerService` | Remove self-proxy anti-pattern |
| **A3** | `LlmService` | Split 628-line class (SRP violation) |
| **D3** | `ProjectService`, others | Replace `RuntimeException` with `ResponseStatusException` |
| **E1** | `FileIndexerService` | Stop swallowing `Throwable` |
| **C3** | `McpController` | DB call inside synchronized map callback |
| **A4** | `McpController` | `new ObjectMapper()` per request |

### 🔵 Polish — Low Risk Improvements

| ID | File | Impact |
|---|---|---|
| **D6/D7** | `Symbol`, `SymbolCall` | Add DB indexes on hot query columns |
| **I5** | (missing) | Add test coverage — currently zero tests |
| **L2** | `LuceneIndexService` | Make index directory configurable |
| **L4** | `LuceneIndexService` | Make search timeout configurable |
| **I2** | `pom.xml` | Remove hardcoded JVM args |
| **C5** | `FileIndexerService` | Add `volatile` to `self` field |

---

*Generated by Antigravity Code Review · java-codebase-mcp · 2026-06-09*
