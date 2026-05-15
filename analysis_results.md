# java-codebase-mcp — Structural Analysis & Improvement Report

> **JCB Index Stats:** 122 files · 1,141 symbols · Status: `COMPLETED`  
> Analyzed: 2026-05-15 | Stack: Spring Boot 4.0.6 · JDK 25 · Lucene 10.1 · JavaParser 3.26.2

---

## 1. Architecture Overview

```
src/main/java/com/mcp/
├── Application.java                   # Entry point — EnableCaching, EnableScheduling, VirtualThreads
├── analysis/                          # CodeAnalyzer (Lucene custom tokenizer)
├── config/                            # VirtualThreadConfig, CacheConfig, OpenAPI, ExceptionHandler, RequestLoggingFilter
├── controller/ (5 controllers)        # REST surface exposed to AI tools via OpenAPI
│   ├── CodebaseController.java        # LARGEST — search, file context, topology, symbols, reconcile
│   ├── ProjectController.java         # CRUD + Git + scan trigger
│   ├── McpController.java             # Sessions, tasks, rules, skills — agent meta-layer
│   ├── BrowserController.java         # Playwright headless browser orchestration
│   └── SystemController.java          # Health / info
├── service/ (15 services)             # Core logic layer
│   ├── FileIndexerService.java        # AST parsing (JavaParser) + DB write + Lucene write
│   ├── FileScannerService.java        # Walk FS, filter, dispatch via Virtual Threads
│   ├── LuceneIndexService.java        # Lucene IndexWriter + SearcherManager per project
│   ├── TopologyService.java           # @Cacheable topology map
│   ├── CodeSummarizerService.java     # Regex-based Java structure + summary extractor
│   ├── EndpointAnalysisService.java   # Controller → Service → Entity tracing
│   ├── ContextMemoryService.java      # In-memory session file tracking
│   ├── GitInfoService.java            # JGit status / commit / staging
│   ├── BrowserSessionManager.java     # Playwright session lifecycle
│   ├── HeadlessBrowserService.java    # Page interaction (click, fill, screenshot, evaluate)
│   ├── SkillService.java              # Markdown → Skill entity extraction
│   ├── ReconciliationService.java     # Stale index cleanup
│   ├── ProjectRuleService.java        # CRUD for project rules
│   ├── ProjectService.java            # Project CRUD + cascade delete
│   └── TaskService.java               # Task + step management
├── dto/                               # 15 DTOs (records + classes)
├── entity/                            # 11 JPA entities (H2)
├── repository/                        # 9 Spring Data JPA repos
├── util/                              # CodeUtils (line numbers), LlmResponseOptimizer (markdown)
├── model/                             # Enums: ProjectStatus, TaskStatus, TaskPriority
├── properties/                        # Type-safe @ConfigurationProperties (Lucene, Cache, Browser)
└── web/                               # (empty — web-related configs in config/)
```

**Data Flow (indexing):**
```
FileScannerService → Virtual Threads → FileIndexerService
    → JavaParser (AST) → SymbolRepository (H2, batch)
    → LuceneIndexService (FSDirectory, NRT)
    → SkillService (for .md files)
```

**Data Flow (AI query):**
```
AI Tool → OpenAPI MCP → REST Controller → LuceneIndexService / SymbolRepository
    → ContextDTO / ContentSearchResult → LlmResponseOptimizer → AI Tool
```

---

## 2. Current Strengths ✅

| Strength | Details |
|---|---|
| **Virtual Threads** | `spring.threads.virtual.enabled=true` + `VirtualThreadConfig` correctly sets executor for massive I/O concurrency |
| **NRT Lucene** | `SearcherManager` + `maybeRefresh()` gives near-real-time search without full commits |
| **Bulk Mode** | `setBulkMode()` defers Lucene commits during initial scan — smart optimization |
| **SHA-256 Incremental Indexing** | Single-pass `DigestInputStream` computes checksum while reading — zero-copy |
| **Caffeine Cache** | Symbol cache + topology cache with configurable TTL and max-size |
| **JDBC Batching** | `batch_size=50` + `order_inserts=true` — proper Hibernate batch config |
| **Conditional ETags** | `If-None-Match` support in `getFileContext` prevents re-sending unchanged content |
| **`@JsonInclude(NON_NULL)`** | `ContextDTO` suppresses null fields in JSON output — reduces payload size |
| **`.gitignore` awareness** | `IgnoreNode` from JGit correctly respects gitignore rules during scanning |
| **Endpoint Analysis** | Deep controller-to-entity tracing for AI context — unique capability |

---

## 3. Issues & Improvement Areas

### 🔴 HIGH PRIORITY — AI Context Quality & Token Efficiency

#### 3.1 `getFileContext` Always Reads Full File Content From Disk
**Location:** `CodebaseController.java:117`

```java
String content = Files.readString(fullPath); // always reads full file
```

Even in `structure` and `summary` modes, the **full raw file is read into memory** and then trimmed. There is no size guard. A 500KB `.java` file with 10K lines will be fully loaded, formatted with line numbers, and returned to the AI tool — consuming massive tokens.

**Fix:** Add file size gating and line count limits per format mode.

---

#### 3.2 `LlmResponseOptimizer.toMarkdown()` is Minimal
**Location:** `LlmResponseOptimizer.java`

The markdown formatter:
- Only supports 5 language hints (java, py, js, ts, md) — missing `.tsx`, `.vue`, `.css`, `.yaml`, `.sql`, `.properties`
- Caps symbols at `20` but emits all content without truncation
- Has **no compact mode** — full code block is always included  
- Doesn't emit metadata (checksum, lastScanned, fileSize) that would help AI tools assess freshness
- Doesn't strip Java import blocks (high noise, low signal for AI)

---

#### 3.3 `CodeSummarizerService.extractStructure()` is Fragile
**Location:** `CodeSummarizerService.java`

Uses **character-level brace counting** — not AST-aware. Issues:
- Braces in string literals (`"{"`) will corrupt depth tracking
- Multi-class files with static initializers will produce wrong output
- No Javadoc preservation in `structure` mode
- `createIntelligentSummary()` only extracts ONE class name and ONE Javadoc — misses annotations like `@RestController`, `@Service`, method signatures, return types, parameters

---

#### 3.4 `getBatchContext` Is Sequential and Casts Unsafely
**Location:** `CodebaseController.java:151-163`

```java
for (String filePath : filePaths) {
    ResponseEntity<Object> response = getFileContext(...); // sequential!
    result.put(filePath, (ContextDTO) response.getBody()); // unsafe cast
```

Batch context fetches files **one by one** despite Virtual Thread availability. The unsafe cast will fail if the response is a markdown `String` (when format = "markdown").

---

#### 3.5 `ContextMemoryService` Is Purely In-Memory With No Eviction
**Location:** `ContextMemoryService.java`

Session data lives in a `ConcurrentHashMap` forever. Long-running AI agent sessions accumulate indefinitely. No TTL, no max size, no persistence across restarts. This means:
- Memory leak risk in long-running deployments
- AI tools that reconnect get no session continuity

---

#### 3.6 Topology Response Dumps Full Dependency Graph
**Location:** `TopologyService.java:80`

The `dependencies` map in `getProjectTopology()` contains **every import of every Java file** — hundreds of entries including JDK/Spring imports that add zero value for AI context. For a 50-file project this is a ~15KB JSON object.

---

### 🟡 MEDIUM PRIORITY — Performance

#### 3.7 `LuceneIndexService.scheduleCommits()` — No SearcherManager Refresh After Commit
**Location:** `LuceneIndexService.java:85-97`

```java
@Scheduled(fixedDelay = 5000)
public void scheduleCommits() {
    writers.forEach((projectId, writer) -> {
        if (writer.hasUncommittedChanges()) {
            writer.commit(); // ✅ commits
            // ❌ SearcherManager is NOT refreshed after scheduled commit!
        }
    });
}
```

After a scheduled commit, `searcherManagers.get(projectId).maybeRefresh()` is NOT called. Searches will miss recently-committed data until the next explicit refresh.

---

#### 3.8 `ContentSearchResult` Stores Full File Content in Lucene
**Location:** `LuceneIndexService.java:149`

```java
doc.add(new TextField("content", content, Field.Store.YES));
```

`Field.Store.YES` stores the raw file content in the Lucene segment. For a large Java file this duplicates data between H2, the filesystem, and Lucene. Consider `Field.Store.NO` and re-reading the file only when a match is found — this can significantly reduce Lucene index size.

---

#### 3.9 `Symbol` Entity Has No `lineNumber` / `signature` Fields
**Location:** `entity/Symbol.java`

```java
// Missing: lineNumber, signature, returnType, modifiers, annotations
```

The AST visitor in `FileIndexerService` extracts `name` and `type` but discards:
- Line number (critical for AI to navigate to exact location)
- Method signature / parameters
- Return type
- Visibility modifiers
- Annotations (`@Override`, `@Transactional`, `@GetMapping(...)`)

AI tools must re-read the whole file to understand what a symbol does.

---

#### 3.10 `TopologyService` Loads ALL Symbols Into Memory
**Location:** `TopologyService.java:67`

```java
List<Symbol> allSymbols = symbolRepository.findByProjectId(projectId);
```

For a large project this loads every symbol into JVM heap to compute a frequency map. Use a native query instead:
```sql
SELECT name, COUNT(*) FROM symbols WHERE project_id = ? GROUP BY name ORDER BY COUNT(*) DESC LIMIT 20
```

---

#### 3.11 `ParserConfiguration` Uses `JAVA_21` Level for a Java 25 Project
**Location:** `FileIndexerService.java:86`

```java
private static final ParserConfiguration JAVA_PARSER_CONFIG = new ParserConfiguration()
    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
```

The project compiles with JDK 25 features. Java 25 introduces new syntax (patterns, unnamed variables, etc.) that JavaParser at `JAVA_21` level may fail to parse, causing `extractJavaData` to silently return empty symbols.

---

### 🟢 LOW PRIORITY / Enhancement Opportunities

#### 3.12 `McpController` Creates Its Own `ScheduledExecutorService` At Startup
**Location:** `McpController.java` (imports `Executors`, `ScheduledExecutorService`)

Uses a raw `Executors.newSingleThreadScheduledExecutor()` that is NOT a Virtual Thread pool and NOT registered with Spring's lifecycle. Should use `@Scheduled` or the injected `applicationTaskExecutor`.

---

#### 3.13 No Pagination in Symbol Search
**Location:** `CodebaseController.java:207`

`searchSymbols` applies limit in Java after loading all DB results:
```java
return symbols.stream().limit(limit).map(...)
```
The DB query `findByProjectIdAndNameContainingIgnoreCase` fetches all matching rows. Use `Pageable` instead.

---

#### 3.14 `agent-config.json` Points to Wrong Port
**Location:** `agent-config.json:9-11`

```json
"--openapi-spec", "http://localhost:8080/v3/api-docs",
"--api-base-url", "http://localhost:8080"
```

But `application.properties` sets `server.port=9696`. The agent config is stale — AI tools pointing at this config will fail to connect.

---

#### 3.15 `SymbolDTO` Lacks File-Relative Path
**Location:** `dto/SymbolDTO.java`

```java
public record SymbolDTO(Long id, String name, SymbolType type, String filePath) {}
```

`filePath` is an **absolute path** (e.g., `D:\Github\project\src\...`). AI tools typically want relative paths. The full absolute path wastes tokens and leaks machine-specific info.

---

## 4. Prioritized Recommendations

### Priority 1 — Fix Token Waste Immediately

| # | Action | Impact |
|---|---|---|
| A | Add max file size limit (e.g., 100KB) and max line cap (e.g., 2000 lines) before sending content to AI | 🔥 Token reduction |
| B | Strip `import` statements from Java content in `structure` format | 🔥 Token reduction |
| C | Fix `LlmResponseOptimizer` language map (add tsx, vue, css, yaml, sql, properties, xml) | Quality |
| D | Fix `agent-config.json` port: `8080` → `9696` | 🔴 Correctness |

### Priority 2 — Enrich Symbol Metadata (Most Impactful for AI Context)

| # | Action | Impact |
|---|---|---|
| E | Add `lineNumber`, `signature`, `returnType`, `modifiers` to `Symbol` entity + AST extraction | 🔥 AI context quality |
| F | Add `@RestController`, `@Service`, `@Scheduled`, `@GetMapping` annotation extraction to symbol metadata | AI context quality |
| G | Return relative paths in `SymbolDTO` instead of absolute | Token reduction + privacy |

### Priority 3 — Fix Bugs & Performance

| # | Action | Impact |
|---|---|---|
| H | Call `searcherManagers.get(projectId).maybeRefresh()` after scheduled commit in `scheduleCommits()` | 🐛 Correctness |
| I | Upgrade `JAVA_21` parser level to `JAVA_21` (latest stable JavaParser supports) or handle unknown syntax gracefully | Correctness |
| J | Fix `getBatchContext` to use parallel `CompletableFuture` with VT executor | Performance |
| K | Add `@Query` to TopologyService's top symbols: use a GROUP BY SQL query instead of in-memory stream | Performance |
| L | Paginate `searchSymbols` with `Pageable` at DB level | Performance |

### Priority 4 — AI Context Enrichment

| # | Action | Impact |
|---|---|---|
| M | Rewrite `CodeSummarizerService.extractStructure()` to use JavaParser AST (already a dependency) instead of brace-counting regex | AI context quality |
| N | Add `createIntelligentSummary()` to extract: class annotations, method signatures, `@Operation` descriptions from Swagger annotations | AI context quality |
| O | Enhance topology response: filter out JDK/Spring imports, group files by package, add package-level description | AI context quality |
| P | Add Caffeine TTL to `ContextMemoryService` (e.g., 2h TTL, 1000 max sessions) | Memory safety |

---

## 5. Quick Wins Code Sketches

### A — File Size Guard in `getFileContext`
```java
// In CodebaseController.getFileContext()
long fileBytes = Files.size(fullPath);
if (fileBytes > 200_000 && "full".equalsIgnoreCase(format)) {
    format = "structure"; // auto-downgrade for large files
}
```

### H — Fix `scheduleCommits()` Refresh Bug
```java
@Scheduled(fixedDelay = 5000)
public void scheduleCommits() {
    writers.forEach((projectId, writer) -> {
        try {
            if (writer.hasUncommittedChanges()) {
                writer.commit();
                SearcherManager sm = searcherManagers.get(projectId); // ADD THIS
                if (sm != null) sm.maybeRefresh();                    // ADD THIS
            }
        } catch (IOException e) { ... }
    });
}
```

### E — Add Symbol Line Numbers
```java
// In FileIndexerService visitor:
@Override
public void visit(MethodDeclaration n, Object arg) {
    Symbol s = createSymbol(n.getNameAsString(), SymbolType.METHOD);
    n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
    s.setSignature(n.getDeclarationAsString(false, false, true));
    symbols.add(s);
    ...
}
```

### D — Fix agent-config.json
```json
{
  "mcpServers": {
    "java-codebase": {
      "command": "npx",
      "args": ["-y", "@ivotoby/openapi-mcp-server",
               "--openapi-spec", "http://localhost:9696/v3/api-docs",
               "--api-base-url", "http://localhost:9696"]
    }
  }
}
```

---

## 6. Architecture Assessment Summary

| Dimension | Rating | Notes |
|---|---|---|
| Concurrency Model | ⭐⭐⭐⭐⭐ | Virtual Threads + CompletableFuture — excellent |
| Indexing Pipeline | ⭐⭐⭐⭐ | Good but needs Java 25 parser level fix |
| Search Quality | ⭐⭐⭐⭐ | NRT Lucene is solid; scheduled commit refresh bug is minor |
| AI Context Quality | ⭐⭐⭐ | Content truncation missing; symbol metadata too sparse |
| Token Efficiency | ⭐⭐⭐ | ETags good; full-file reads + import noise hurts |
| Bug Count | ⭐⭐⭐ | 3 bugs found: port mismatch, commit refresh, unsafe batch cast |
| Code Clarity | ⭐⭐⭐⭐ | Clean package structure; `CodebaseController` is slightly overloaded |
| DB Design | ⭐⭐⭐⭐ | Good indexes; `symbol` table could store richer metadata |
