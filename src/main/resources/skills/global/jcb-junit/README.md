# jcb-junit

Terse, exhaustive JUnit 5 unit test generator for Spring service implementations.

## What it does

Generates self-contained JUnit 5 test classes using Mockito and `@SpringBootTest`. Enforces complete branch coverage by testing all if/else conditions, null parameters, exception throwing, and correct Mockito verifications (`times`, `never`).

## How to invoke

```
/jcb-junit
```

Also triggers on phrases like "generate tests", "create junit test", "write unit tests".

## See also

- [`SKILL.md`](./SKILL.md) — full AGENT-facing instructions
- [Jcb README](../../README.md) — repo overview
