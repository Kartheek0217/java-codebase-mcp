# MCP Tools API Reference

This document outlines all the officially supported MCP tools exposed by the Java Codebase Agent, their corresponding REST endpoints, and usage instructions. These endpoints were recently de-multiplexed to ensure clear, single-purpose paths that perfectly align with OpenAPI generator specifications.

Total official tools exposed: **41** (Keeping strictly under the 45-tool IDE constraint).

---

## 1. Codebase Discovery & Reading (15 Tools)

| Tool Name | Method & Endpoint | Description & Key Params |
| :--- | :--- | :--- |
| `read_file` | `GET /api/mcp/codebase/file` | Read a single file with symbols. Params: `projectId`, `filePath`, `format`. |
| `search_content` | `GET /api/mcp/codebase/search` | Full-text Lucene search across indexed files. Params: `projectId`, `query`. |
| `search_changed_content` | `GET /api/mcp/codebase/search-changed` | Search restricted to uncommitted files. Params: `projectId`, `query`. |
| `search_symbols` | `GET /api/mcp/codebase/symbols` | Search classes, methods, or fields by name. Params: `projectId`, `query`, `type`. |
| `find_files` | `GET /api/mcp/codebase/files` | Find indexed files by path fragment. Params: `projectId`, `query`. |
| `suggest_context` | `GET /api/mcp/codebase/suggest` | Combined symbol + content search. Returns top hits. Params: `projectId`, `query`. |
| `get_session_history` | `GET /api/mcp/codebase/history` | Return file paths accessed in a session. Params: `projectId`, `sessionId`. |
| `get_project_topology` | `GET /api/mcp/codebase/topology` | Return project package structure and dependency graph. Params: `projectId`. |
| `summarize_file` | `GET /api/mcp/codebase/summarize` | Generate an AI summary of a file. Params: `projectId`, `filePath`. |
| `analyze_endpoint` | `GET /api/mcp/codebase/analyze-endpoint`| Trace a controller endpoint down to entity level. Params: `projectId`, `controllerName`, `methodName`. |
| `scan_project` | `POST /api/mcp/codebase/scan` | Trigger directory scan for new/changed files. Params: `projectId`. |
| `reconcile_index` | `POST /api/mcp/codebase/reconcile` | Reconcile symbol index against the filesystem. Params: `projectId`. |
| `batch_read_files` | `POST /api/mcp/codebase/batch` | Fetch content for multiple files in parallel. Params: `projectId`, body: array of paths. |
| `get_symbol_detail` | `GET /api/mcp/symbols/{id}` | Retrieve full Symbol entity. Params: `id` (path). |
| `get_call_hierarchy`| `GET /api/mcp/symbols/{id}/hierarchy`| Call hierarchy (incoming/outgoing calls). Params: `id` (path). |

---

## 2. Agent & AI Interactions (10 Tools)

*Note: All tools below stream Server-Sent Events (SSE) back to the client. Synchronous equivalents exist via `/sync` but are hidden from the OpenAPI schema to preserve tool counts.*

| Tool Name | Method & Endpoint | Description & Key Params |
| :--- | :--- | :--- |
| `explain_symbol` | `POST /api/agent/explain-symbol` | Explain a code symbol. Params: `projectId`, `symbolId`. |
| `explain_file` | `POST /api/agent/explain-file` | Explain what a source file does. Params: `projectId`, `filePath`. |
| `ask_question` | `POST /api/agent/ask` | Ask a free-form question about the codebase. Params: `projectId`, body: `{question}`. |
| `code_review` | `POST /api/agent/code-review` | Generate a code review for a file. Params: `projectId`, `filePath`. |
| `code_refactor` | `POST /api/agent/code-refactor` | Suggest refactoring improvements for a file. Params: `projectId`, `filePath`. |
| `code_optimise` | `POST /api/agent/code-optimise` | Suggest performance optimisations (alias of refactor). Params: `projectId`, `filePath`. |
| `web_search` | `POST /api/agent/web-search` | Search the web and summarise results. Params: `projectId`, `query` or `url`. |
| `code_commit` | `POST /api/agent/code-commit` | Generate Conventional Commit from git diff. Params: `projectId`, `diff`. |
| `java_doc` | `POST /api/agent/java-doc` | Generate Javadoc for all public methods in a file. Params: `projectId`, `filePath`. |
| `junit_test_cases`| `POST /api/agent/junit-test-cases` | Generate JUnit 5 test class with 100% branch coverage. Params: `projectId`, `filePath`. |

---

## 3. Project Management (6 Tools)

| Tool Name | Method & Endpoint | Description & Key Params |
| :--- | :--- | :--- |
| `list_projects` | `GET /api/projects` | List projects (optionally by summary or detail). Query param: `view`. |
| `crt-project` | `POST /api/projects` | Create a new project and index it. Body: `name`, `rootPath`. |
| `get_project_details`| `GET /api/projects/{id}` | Read project data, stats, or git-status. Query param: `view`. |
| `reindex_project` | `POST /api/projects/{id}/reindex` | Trigger a full re-index of all project files. |
| `manage_project_vcs` | `POST /api/projects/{id}/vcs` | Stage, discard, or commit files. Query: `action` (stage/discard/commit), `message` (if commit). Body: array of glob patterns. |
| `del-project` | `DELETE /api/projects/{id}` | Permanently delete a project and its index. |

---

## 4. Task Management (5 Tools)

| Tool Name | Method & Endpoint | Description & Key Params |
| :--- | :--- | :--- |
| `list_tasks` | `GET /api/mcp/tasks` | Retrieve all tasks for a project. Params: `projectId`. |
| `create_task` | `POST /api/mcp/tasks` | Create a new task. Body: `CreateTaskRequest`. |
| `update_task` | `PUT /api/mcp/tasks/{id}` | Update an existing task. Body: `TaskDTO`. |
| `delete_task` | `DELETE /api/mcp/tasks/{id}` | Delete a task by ID. |
| `update_task_step`| `PUT /api/mcp/tasks/{id}/step` | Update the status of a single task step. Params: `stepId`, `status`. |

---

## 5. Skills & Rules Management (8 Tools)

| Tool Name | Method & Endpoint | Description & Key Params |
| :--- | :--- | :--- |
| `get_skills` | `GET /api/mcp/skills` | Retrieve built-in and project-specific skills. Params: `projectId` (optional). |
| `learn_skill_from_url`| `POST /api/mcp/skills/learn-url` | Learn a skill from a URL. Params: `projectId`, `url`. |
| `learn_skill_from_file`| `POST /api/mcp/skills/learn-file`| Learn a skill from a local file. Params: `projectId`, `filePath`. |
| `clear_skills` | `DELETE /api/mcp/skills` | Remove all project-specific learned skills. Params: `projectId`. |
| `list_rules` | `GET /api/mcp/rules` | Retrieve all coding rules for a project. Params: `projectId`. |
| `create_rule` | `POST /api/mcp/rules` | Add a new rule. Body: `RuleDTO`. |
| `delete_rule` | `DELETE /api/mcp/rules/{id}` | Delete a single rule by ID. |
| `clear_rules` | `DELETE /api/mcp/rules` | Delete all rules for a project. Params: `projectId`. |

---

## 6. System & Sessions (7 Tools)

| Tool Name | Method & Endpoint | Description & Key Params |
| :--- | :--- | :--- |
| `get-system-status`| `GET /api/system/status` | Retrieve system health, info, or agent-status. Header: `X-View`. |
| `lst-sessions` | `GET /api/mcp/sessions` | List active browser sessions. |
| `crt-session` | `POST /api/mcp/sessions` | Start headless browser. Body: `BrowserSessionRequest`. |
| `get-session-state`| `GET /api/mcp/sessions/{sessionId}/state`| Read browser state (HTML content). |
| `browser` | `POST /api/mcp/sessions/{sessionId}/action`| Execute browser action (navigate/click/fill/eval). Header: `X-Action`. |
| `close-session` | `DELETE /api/mcp/sessions/{sessionId}`| Terminate a browser session. |
| `get-session` | `GET /api/mcp/agent/sessions/{sessionId}`| Get context history of an agent session. |
