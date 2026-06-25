# MCP Tools API Reference

This document outlines all the officially supported MCP tools exposed by the Java Codebase Agent, their corresponding REST endpoints, and usage instructions. These endpoints were recently de-multiplexed to ensure clear, single-purpose paths that perfectly align with OpenAPI generator specifications.

Total official tools exposed: **64**

---

## 1. Codebase Discovery & Reading (15 Tools)

| Tool Name | Description & Key Params |
| :--- | :--- |
| `read-file` | Read a single file with its symbols and metadata. |
| `search-content` | Full-text Lucene search across all indexed files. |
| `search-changed-content` | Full-text search restricted to uncommitted files only. |
| `search-symbols` | Search for classes, methods, constructors, or fields by name. |
| `find-files` | Find indexed files whose paths contain the query string. |
| `suggest-context` | Combined symbol + content search for relevant code context. |
| `get-session-history` | Return file paths accessed in a session. |
| `get-project-topology` | Return project package structure and dependency graph. |
| `summarize-file` | Generate an AI summary of a file. |
| `analyze` | Trace a controller endpoint down to entity level. |
| `scan-project` | Trigger a directory scan to detect new/changed/deleted files. |
| `reconcile-index` | Reconcile the symbol index against the current filesystem state. |
| `batch-read-files` | Fetch content for multiple files in parallel. |
| `get-symbol-detail` | Retrieve full Symbol entity by ID. |
| `get-call-hierarchy` | Retrieve call hierarchy for the symbol. |

---

## 2. Agent & AI Interactions (22 Tools)

| Tool Name | Description & Key Params |
| :--- | :--- |
| `explain-symbol` | Explain a code symbol in plain English. Params: `symbolId` |
| `explain-symbol-sync` | Make a POST request to /api/agent/explain-symbol/sync |
| `explain-file` | Explain what a source file does. Params: `filePath` |
| `explain-file-sync` | Make a POST request to /api/agent/explain-file/sync |
| `ask-question` | Ask a free-form question about the codebase. Body: `{question}` |
| `ask-question-sync` | Make a POST request to /api/agent/ask/sync |
| `code-review` | Generate a code review for a file. Params: `filePath` |
| `code-review-sync` | Make a POST request to /api/agent/code-review/sync |
| `code-refactor` | Suggest refactoring improvements for a file. Params: `filePath` |
| `code-refactor-sync` | Make a POST request to /api/agent/code-refactor/sync |
| `code-optimise` | Suggest performance optimisations for a file. Params: `filePath` |
| `code-optimise-sync` | Make a POST request to /api/agent/code-optimise/sync |
| `web-search` | Search the web and summarise results. Params: `query` or `url` |
| `web-search-sync` | Make a POST request to /api/agent/web-search/sync |
| `code-commit` | Generate a Conventional Commits message from a git diff. Params: `diff` |
| `code-commit-sync` | Make a POST request to /api/agent/code-commit/sync |
| `java-doc` | Generate Javadoc for all public methods in a file. Params: `filePath` |
| `java-doc-sync` | Make a POST request to /api/agent/java-doc/sync |
| `junit-test-cases` | Generate JUnit 5 test class with 100% branch coverage. Params: `filePath` |
| `junit-test-cases-sync`| Make a POST request to /api/agent/junit-test-cases/sync |
| `submit-agent-task` | Submit a background agent task. POST to /api/agent/task/submit/{action} |
| `get-agent-task` | Retrieve the status and result of a background agent task. GET to /api/agent/task/{id} |

---

## 3. Project Management (6 Tools)

| Tool Name | Description & Key Params |
| :--- | :--- |
| `get-projects` | Retrieve project list. Optional view parameter can be 'list' or 'summary'. |
| `crt-project` | Create a new project and start background indexing of its root directory. |
| `get-project` | Read project data. Optional view parameter can be 'detail', 'stats', or 'git-status'. |
| `reindex-project` | Trigger a full re-index of all project files. |
| `mng-project-vcs` | Execute a VCS operation (stage, discard, commit) on a project. |
| `del-project` | Permanently delete a project and remove all its indexed symbols, files, and metadata. |

---

## 4. Task Management (5 Tools)

| Tool Name | Description & Key Params |
| :--- | :--- |
| `get-tasks` | Retrieve all tasks for a project. Query param: `projectId`. |
| `crt-task` | Create a new task. Body: `CreateTaskRequest`. |
| `upd-task` | Update an existing task. Path param: `id`. Body: `TaskDTO`. |
| `del-task` | Delete a task by ID. Path param: `id`. |
| `upd-task-step` | Update the status of a single task step. Path param: `id`, Query params: `stepId`, `status`. |

---

## 5. Skills & Rules Management (8 Tools)

| Tool Name | Description & Key Params |
| :--- | :--- |
| `get-skills` | Retrieve available skills for an agent. |
| `learn-skill-url` | Fetch and learn a skill from a URL or built-in path. |
| `learn-skill-file` | Learn a skill from a local file path. |
| `clear-skills` | Remove all project-specific learned skills. |
| `get-rules` | Retrieve all coding rules associated with a project. |
| `crt-rule` | Add a new rule. Body: `RuleDTO`. |
| `del-rule` | Delete a rule by ID. |
| `clear-rules` | Delete all rules for a project. |

---

## 6. System & Sessions (8 Tools)

| Tool Name | Description & Key Params |
| :--- | :--- |
| `get-system-status` | Read system state via the X-View request header (health, info, agent-status). |
| `lst-sessions` | List all active or historical browser sessions. |
| `crt-session` | Initialize a new headless browser context. |
| `get-session-state` | Read the current state of the browser page within a session. |
| `browser` | Perform a browser interaction within an active session. |
| `close-session` | Terminate a browser session and release all Playwright resources. |
| `start-session` | Start a new AI agent session bound to a project. |
| `get-session` | Retrieve metadata for an active agent session. |
