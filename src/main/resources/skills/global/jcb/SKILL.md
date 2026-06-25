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
| Find files by path/name | `find_files` | `query=<fragment>` |
| Search file contents (full-text) | `search_content` | `query=<term>` |
| Search classes / methods / fields | `search_symbols` | `query=<name>`, `type=CLASS\|METHOD\|FIELD\|CONSTRUCTOR` |
| Read a single file with symbols | `read_file` | `filePath=<rel-path>`, `format=full\|structure\|summary\|numbered\|markdown` |
| Combined symbol + content context | `suggest_context` | `query=<term>` |
| Get package/dependency topology | `get_project_topology` | None |
| Search only uncommitted files | `search_changed_content` | `query=<term>` |
| Get AI summary of a file | `summarize_file` | `filePath=<rel-path>` |
| Trace controller → entity | `analyze_endpoint` | `controllerName`, `methodName` |
| Read multiple files at once | `batch_read_files` | body=`["rel/path/A.java","rel/path/B.java"]` |
| Scan for new/changed/deleted files | `scan_project` | None |
| Reconcile symbol index | `reconcile_index` | None |
| Get symbol details by ID | `get_symbol_detail` | `id=<symbolId>` |
| Get call hierarchy (in/outbound) | `get_call_hierarchy` | `id=<symbolId>` |

---

## Tools Reference

**Codebase Discovery:** `read_file`, `search_content`, `search_changed_content`, `search_symbols`, `find_files`, `suggest_context`, `get_session_history`, `get_project_topology`, `summarize_file`, `analyze_endpoint`, `scan_project`, `reconcile_index`, `batch_read_files`, `get_symbol_detail`, `get_call_hierarchy`.

**Project Management:** `list_projects`, `get_project_details`, `reindex_project`, `manage_project_vcs`, `crt-project`, `del-project`.

**Task Management:** `list_tasks`, `create_task`, `update_task`, `delete_task`, `update_task_step`.

**Rules & Skills:** `list_rules`, `create_rule`, `delete_rule`, `clear_rules`, `get_skills`, `learn_skill_from_url`, `learn_skill_from_file`, `clear_skills`.

**Agent:** `explain_symbol`, `explain_file`, `ask_question`, `code_review`, `code_refactor`, `code_optimise`, `web_search`, `code_commit`, `java_doc`, `junit_test_cases`, `submit_agent_task`, `get_agent_task`.

**Session Management:** `lst-sessions`, `crt-session`, `get-session-state`, `browser`, `close-session`, `get-session`.

**System Status:** `get-system-status` (Check `X-View: health` before project ops, and `X-View: agent-status` before dispatching Agent actions).

---

## System Health

Always call `get-system-status` (`X-View: health`) before any project operations to confirm the server is ready. Check `X-View: agent-status` before dispatching any agent action.

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
