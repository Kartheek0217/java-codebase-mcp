# java-clean-code

Refactoring branching logic, guard clauses, functional command maps, rule engines, Enum pointer equality, and switch expressions.

## What it does

### Java Clean Code & Branching Architecture Guide

Senior engineers actively avoid deeply nested `if-else` chains because procedural branching logic becomes unreadable, untestable, and impossible to safely extend without introducing regressions.

```text
+-----------------------------------+-----------------------------------+
| Junior Mindset                    | Senior Mindset                    |
+-----------------------------------+-----------------------------------+
| "What condition do I check next?" | "What rule am I implementing?"    |
| Logic is tightly glued together.  | Logic is extracted into components|
| Centralized monolithic blocks.    | Distributed, testable behaviors.  |
+-----------------------------------+-----------------------------------+
```

## How to invoke

```
/java-clean-code
```

## See also

- [`SKILL.md`](./SKILL.md) — full LLM-facing instructions
- [Jcb README](../../README.md) — repo overview
