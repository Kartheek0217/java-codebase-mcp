# caveman-junit

Terse, exhaustive JUnit 5 unit test generator for Spring service implementations.

## What it does

Generates self-contained JUnit 5 test classes using Mockito and `@SpringBootTest`. Enforces complete branch coverage by testing all if/else conditions, null parameters, exception throwing, and correct Mockito verifications (`times`, `never`).

## How to invoke

```
/caveman-junit
```

Also triggers on phrases like "generate tests", "create junit test", "write unit tests".

## See also

- [`SKILL.md`](./SKILL.md) — full LLM-facing instructions
- [Caveman README](../../README.md) — repo overview
