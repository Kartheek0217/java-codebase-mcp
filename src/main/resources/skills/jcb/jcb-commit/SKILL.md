---
name: jcb-commit
description: >
  Ultra-compressed commit message generator. Follows Conventional Commits format. Subject ≤50 chars. Body only when "why" is non-obvious or for breaking changes.
---

# JCB Commit Generator

Generate precise, fluff-free commit messages in Conventional Commits format. Focus on *why* over *what*.

## Rules
- **Subject**: `<type>(<scope>): <imperative summary>` (e.g., `feat(api): add GET /users/:id/profile`). Hard cap 72 chars. No trailing period.
- **Body**: Only if *why* is non-obvious, breaking changes (`!`), security fixes, or migration notes. Wrap at 72 chars.
- **Exclusions**: Never write "This commit does X", "I/we", AI attributions, or restate filenames.

## JCB Tools Integration
- Check modified files: `mcp_jcb_get-project-git-status` / `mcp_jcb_search-changed`.
- Stage files: `mcp_jcb_stage-files`.
- Commit: `mcp_jcb_commit`.

## Boundaries
- Output generated commit message inside a code block ready to paste.
- Do not execute CLI git commands directly.
- Do not automatically stage or commit changes to git after any edit/update/delete. Wait for explicit user request/command before committing.
- Revert to standard commit style if user says `stop jcb-commit`.