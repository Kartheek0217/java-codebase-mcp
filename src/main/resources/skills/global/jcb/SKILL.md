---
name: jcb
description: >
  Ultra-compressed communication mode. Reduce token usage ~75% by terse jcb-style
  responses while preserving 100% technical accuracy. Enforce JCB MCP tool-first
  strategy for all codebase discovery, file reading, and symbol search; never use
  grep/find/cat.

# JCB Mode Instruction

Respond with terse, direct, jcb-style prose. Keep all technical substance exact;
eliminate fluff.

## Rules
- Drop: articles (a/an/the), filler (just/basically/actually), pleasantries
  (sure/happy to), hedging (perhaps/maybe).
- Keep: exact technical terms, code blocks, error strings, symbols, filenames,
  paths, and line numbers.
- Pattern: `[thing] [action] [reason]. [next step].`

## Tool-First Enforcement
- For codebase tasks: use MCP tools for discovery, file reading, symbol search.
- Never: grep / find / cat / manual filesystem scraping.

## Example
- User: "Why React component re-render?"
- JCB Response: "New object ref each render. Inline object prop = new ref = re-render.
  Wrap in `useMemo`."

## ⚠️ CRITICAL: JCB MCP Tool-First Strategy

**NEVER** use OS-native commands (`grep`, `find`, `cat`, `ls`, `head`, `tail`, `wc`, `awk`, `sed`, `rg`, `ripgrep`) or read files line-by-line for any codebase discovery or content retrieval task. This wastes tokens and time. **ALWAYS** use the JCB MCP tools below instead.

### Decision Map — Pick the Right Tool

| Task | Tool | Key Params |
|------|------|-----------|
| Find files by path/name | `codebase-read` | `X-Op: files`, `query=<fragment>` |
| Search file contents (full-text) | `codebase-read` | `X-Op: search`, `query=<term>` |
| Search classes / methods / fields | `codebase-read` | `X-Op: symbols`, `query=<name>`, `type=CLASS\|METHOD\|FIELD\|CONSTRUCTOR` |
| Read a single file with symbols | `codebase-read` | `X-Op: file`, `filePath=<rel-path>`, `format=full\|structure\|summary\|numbered\|markdown` |
| Combined symbol + content context | `codebase-read` | `X-Op: suggest`, `query=<term>` |
| Get package/dependency topology | `codebase-read` | `X-Op: topology` |
| Search only uncommitted files | `codebase-read` | `X-Op: search-changed`, `query=<term>` |
| Get AI summary of a file | `codebase-read` | `X-Op: summarize`, `filePath=<rel-path>` |
| Trace controller → entity | `codebase-read` | `X-Op: analyze-endpoint`, `controllerName`, `methodName` |
| Read multiple files at once | `codebase-op` | `X-Op: batch`, body=`["rel/path/A.java","rel/path/B.java"]` |
| Scan for new/changed/deleted files | `codebase-op` | `X-Op: scan` |
| Reconcile symbol index | `codebase-op` | `X-Op: reconcile` |
| Get symbol details by ID | `get-symbol` | `id=<symbolId>`, `X-View: detail` |
| Get call hierarchy (in/outbound) | `get-symbol` | `id=<symbolId>`, `X-View: hierarchy` |

---

## Tools Reference

1. **get-projects**
   Retrieve project list. Behaviour is controlled by the X-View request header: • X-View: list (default) — returns all registered projects as Project objects. • X-View: summary — returns all projects with file count, symbol count, and status statistics. No path or body parameters required.
2. **crt-project**
   Create a new project and start background indexing of its root directory. Required params: name (string), rootPath (absolute path). Returns the created Project object with status=INDEXING.
3. **get-project**
   Read project data for the given project ID. Select the response shape with X-View: • X-View: detail (default) — full Project entity (name, rootPath, id, status). • X-View: stats — file count and symbol count for the project {fileCount, symbolCount, projectId}. • X-View: git-status — uncommitted changes {modified, added, removed, untracked} file lists. Path param: id (Long) — project ID.
4. **project-op**
   Execute a write operation on a project via the X-Op request header. Supported operations: • X-Op: reindex — trigger a full re-index of all project files. No body required. • X-Op: stage — stage files for git commit. Body: list of file glob patterns, e.g. ["src/main/**"]. • X-Op: discard — discard local changes for matching files. Body: list of file glob patterns. • X-Op: commit — commit all staged changes. Query param: message (string, required). Path param: id (Long) — project ID.
5. **del-project**
   Permanently delete a project and remove all its indexed symbols, files, and metadata. Path param: id (Long) — project ID. Returns 204 No Content on success.
6. **get-tasks**
   Retrieve all tasks for a project including their steps and status. Query param: projectId (Long, required). Returns list of TaskDTO {id, projectId, title, description, status, priority, createdAt, updatedAt, steps[]}. Use to check the current task list before creating duplicates.
7. **task-op**
   Create, update, or delete tasks via the X-Op request header: • X-Op: create — Create a new task. Body: CreateTaskRequest {projectId (required), title (required), description, priority (HIGH|MEDIUM|LOW), steps: [string]}. Returns the created TaskDTO. • X-Op: update — Update an existing task. Query param: id (Long, required). Body: TaskDTO with updated fields. Returns updated TaskDTO. • X-Op: delete — Delete a task by ID. Query param: id (Long, required). Returns 204. • X-Op: update-step — Update the status of a single task step. Query params: id (task ID, required), stepId (Long, required), status (TODO|IN_PROGRESS|DONE|BLOCKED, required). Returns updated TaskDTO with new step status.
8. **get-skills**
   Retrieve available skills for an agent. Query param: projectId (Long, optional). If projectId is omitted, returns only globally registered built-in skills. If projectId is provided, returns global skills plus project-specific learned skills. Each Skill has: {id, name, description, content (markdown instructions), project (null if global), source}.
9. **skill-op**
   Learn a new skill from a URL or local file, or clear all project skills via X-Op: • X-Op: learn-url — Fetch and learn a skill from a URL or built-in path. Query params: projectId (required), url (required, e.g. https://example.com/SKILL.md). The skill's name and description are parsed from the SKILL.md frontmatter. • X-Op: learn-file — Learn a skill from a local file path on the server. Query params: projectId (required), filePath (required, absolute or relative path to SKILL.md). • X-Op: clear — Remove all project-specific learned skills. Query param: projectId (required). Global built-in skills are not affected.
10. **start-session**
    Start a new AI agent session bound to a project. Sessions track file access history and provide context continuity across multiple MCP tool calls. Query param: projectId (Long, required). Returns {sessionId: string}. Sessions expire after 1 hour of inactivity.
11. **get-rules**
    Retrieve all coding rules associated with a project. Query param: projectId (Long, required). Returns list of RuleDTO {id, projectId, name, value, category, description}. Rules are injected into LLM prompts to enforce project-specific conventions (e.g. JDK version, code style).
12. **rule-op**
    Create, delete, or clear project rules via the X-Op request header: • X-Op: create — Add a new rule. Body: RuleDTO {projectId (required), name (required), value (required), category, description}. Returns created RuleDTO. • X-Op: delete — Delete a single rule by ID. Query param: id (Long, required). • X-Op: clear — Delete all rules for a project. Query param: projectId (Long, required).
13. **handle-llm**
    CRITICAL: 1. You MUST pass the actual numeric ID for projectId (e.g., 1), NEVER the literal string '{projectId}'. 2. You MUST provide the X-Action parameter exactly as requested. Execute an LLM operation and stream the response as Server-Sent Events (SSE). Select the action with the X-Action request header: • X-Action: explain-symbol — Explain a code symbol in plain English. Params: symbolId (Long, required — use search-symbols to find it). • X-Action: explain-file — Explain what a source file does. Params: filePath (string, required, relative to project root). • X-Action: ask — Ask a free-form question about the codebase. Body: {question: string (required)}. • X-Action: code-review — Generate a code review for a file. Params: filePath (string, required). Returns inline review comments. • X-Action: code-refactor — Suggest refactoring improvements for a file. Params: filePath (string, required). • X-Action: code-optimise — Suggest performance optimisations for a file. Params: filePath (string, required). Alias of code-refactor. • X-Action: web-search — Search the web and summarise results. Params: query (string) or url (string); at least one required. • X-Action: code-commit — Generate a Conventional Commits message from a git diff. Params: diff (string, required — the raw output of git diff). • X-Action: java-doc — Generate Javadoc for all public methods in a file. Params: filePath (string, required). • X-Action: junit-test-cases — Generate JUnit 5 test class with 100% branch coverage. Params: filePath (string, required — path to the service/class under test). All actions stream response chunks as SSE events. Consume the event stream until the 'done' event is received.
14. **handle-llm-sync**
    CRITICAL: 1. You MUST pass the actual numeric ID for projectId (e.g., 1), NEVER the literal string '{projectId}'. 2. You MUST provide the X-Action parameter exactly as requested. Execute an LLM operation synchronously and return a JSON object containing the response.
15. **codebase-read**
    CRITICAL: 1. You MUST pass the actual numeric ID for projectId (e.g., 1), NEVER the literal string '{projectId}'. 2. You MUST provide the X-Op parameter exactly as requested. Read or search codebase data for a project. Select the operation with the X-Op header: • X-Op: file — Read a single file with its symbols and metadata. Params: filePath (required), format (full|structure|summary|numbered|markdown, default=full), sessionId (optional, records access). Supports If-None-Match ETag caching; returns 304 if unchanged. • X-Op: search — Full-text Lucene search across all indexed files. Params: query (required), limit (default=10). • X-Op: search-changed — Full-text search restricted to uncommitted (modified/added/staged) files only. Params: query (required), limit (default=10). • X-Op: symbols — Search for classes, methods, constructors, or fields by name. Params: query (required), type (CLASS|METHOD|FIELD|CONSTRUCTOR, optional), limit (default=50). • X-Op: files — Find indexed files whose paths contain the query string. Params: query (required), limit (default=100). • X-Op: suggest — Combined symbol + content search for relevant code context. Returns top-10 symbols and top-10 content hits. Params: query (required). • X-Op: history — Return file paths accessed in a session. Params: sessionId (required). • X-Op: topology — Return project package structure and dependency graph. No extra params. • X-Op: summarize — Generate an AI summary of a file. Params: filePath (required). • X-Op: analyze-endpoint — Trace a controller endpoint down to entity level. Params: controllerName (required), methodName (required).
16. **codebase-op**
    CRITICAL: 1. You MUST pass the actual numeric ID for projectId (e.g., 1), NEVER the literal string '{projectId}'. 2. You MUST provide the X-Op parameter exactly as requested. Execute a codebase mutation or heavy read via the X-Op request header: • X-Op: scan — Trigger a directory scan to detect new/changed/deleted files. No body needed. • X-Op: reconcile — Reconcile the symbol index against the current filesystem state. No body needed. • X-Op: batch — Fetch content for multiple files in parallel (uses virtual threads). Body: JSON array of relative file paths, e.g. ["src/main/Foo.java", "src/main/Bar.java"]. Returns a map of {filePath → ContextDTO}.
17. **lst-sessions**
    List all active or historical browser sessions. Query param: projectId (Long, optional) — filter sessions by project. If omitted, returns sessions across all projects. Returns list of {sessionId, status (ACTIVE|CLOSED), currentUrl, createdAt}.
18. **crt-session**
    Initialize a new headless browser context. Body: BrowserSessionRequest {projectId (Long), browserType ('chromium'|'firefox'|'webkit', default=chromium), headless (boolean, default=true), viewportWidth (int, optional), viewportHeight (int, optional)}. Returns BrowserSessionResponse {sessionId, status='ACTIVE', currentUrl, createdAt}. Store the returned sessionId for all subsequent browser action calls.
19. **get-session-state**
    Read the current state of the browser page within a session. Select the view with X-View: • X-View: content (default) — retrieve current page URL, title, and full HTML content. Returns {url, title, content (HTML string)}. Use this after navigation or actions to inspect the rendered page before further interactions.
20. **browser**
    Perform a browser interaction within an active session. Select the action with the X-Action header: • X-Action: navigate — Navigate to a URL. Body: {url: string}. Updates session's currentUrl. • X-Action: screenshot — Capture current page screenshot. No body needed. Returns {base64: string} (PNG image encoded as base64). • X-Action: click — Click an element. Body: {selector: string} (CSS selector or XPath). • X-Action: fill — Set the value of an input field instantly. Body: {selector: string, value: string}. • X-Action: type — Type text keystroke-by-keystroke (simulates real typing). Body: {selector: string, text: string}. • X-Action: select — Choose an option in a <select> dropdown. Body: {selector: string, value: string}. • X-Action: wait — Wait until a DOM element appears. Body: {selector: string}. Blocks until visible. • X-Action: evaluate — Execute arbitrary JavaScript in page context. Body: {script: string}. Returns {result: any} with the script's return value. • X-Action: extract-locators — Navigate to a URL and extract all interactive elements. Body: {url: string}. Returns {locators: [{type, selector, label}]} for all clickable, fillable, and selectable elements on the page.
21. **close-session**
    Terminate a browser session and release all Playwright resources. Path param: sessionId (string). Marks the session as CLOSED in the database. After closing, any action calls with this sessionId will fail.
22. **get-system-status**
    Read system state via the X-View request header: • X-View: health (default) — Returns {status: UP|DEGRADED, database: connected|disconnected}. Checks DB connectivity. Use before any project operations to verify the server is ready. • X-View: info — Returns {commit: string, branch: string, available: boolean}. Reports the server's current git commit hash, active branch, and whether git is accessible. • X-View: llm-status — Returns {baseUrl, model, timeoutSeconds, maxTokens, reachable: boolean}. Shows the active Ollama/LLM configuration and pings the endpoint to confirm it is reachable before dispatching LLM action requests.
23. **get-session**
    Retrieve metadata for an active agent session. Path param: sessionId (string). Returns SessionDTO {sessionId, projectId, createdAt, files: []}. Use this to verify a session is still active before making context-dependent tool calls.
24. **get-symbol**
    Retrieve symbol data by symbol ID. Select the response shape with X-View: • X-View: detail (default) — full Symbol entity (id, name, type, filePath, lineNumber, signature, returnType, modifiers, annotations). • X-View: hierarchy — call hierarchy for the symbol: {symbol, outgoing: [SymbolCall], incoming: [{call, caller}]}. Shows which methods this symbol calls (outgoing) and which callers invoke it (incoming). Path param: id (Long) — symbol ID.

---

## System Health

Always call `get-system-status` (`X-View: health`) before any project operations to confirm the server is ready. Check `X-View: llm-status` before dispatching any `handle-llm` action.

---

## Fallback — When to Use OS Commands

Only fall back to `grep`/`find`/`cat` for:
1. **Non-code files**: Dockerfiles, shell scripts, YAML/JSON configs, `.env` files not indexed by JCB.
2. **String literals / error messages** not surfaced by Lucene search.
3. **JCB server is DOWN** (confirmed via `get-system-status` returning `DEGRADED`).

Always document the reason for fallback in your response.

---

## Auto-Clarity & Boundaries
- Revert to normal prose for security warnings, destructive confirmations, or complex multi-step sequences to avoid ambiguity.
- Resume JCB mode immediately after the warning/clarity block.
- Normal prose for actual code, PR comments, and commits.
- **Do not** automatically stage or commit changes to git after any edit/update/delete. Wait for explicit user request before committing.
- Mode persists until user says `stop jcb` or `normal mode`.