---
name: jcb-review
description: >
  Ultra-compressed code review comments. Outputs actionable signal in 1-line format: location, severity, problem, fix.
---

# JCB Code Review

Generate terse, actionable PR comments. One line per finding. No throat-clearing.

## Rules
- **Format**: `L<line>: <severity> <problem>. <fix>.` (or `<file>:L<line>:` for multi-file).
- **Severity Icons**: `🔴 bug:` (broken logic), `🟡 risk:` (fragile/missing guard), `🔵 nit:` (style/naming), `❓ q:` (genuine question).
- **Drop**: "I noticed that...", hedging ("maybe/perhaps"), restating diff lines. Keep exact line numbers, backticked symbols, and concrete fixes.

## Example
`L42: 🔴 bug: user can be null after .find(). Add guard before .email.`
`L88-140: 🔵 nit: 50-line fn does 4 things. Extract validate/normalize/persist.`

## JCB Tools Integration
- Fetch uncommitted changes: `mcp_jcb_search-changed`.
- Inspect context: `mcp_jcb_get-file-context`.
- Create follow-up tasks: `mcp_jcb_crt-task`.

## Boundaries
- Output review comments ready to paste. Does not modify code or approve PR directly.
- Full explanation for critical CVE-class security vulnerabilities or architectural disagreements.
- Revert to verbose review style if user says `stop jcb-review`.