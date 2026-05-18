---
name: jcb-junit
description: >
  Ultra-compressed JUnit 5 test generator for Spring service implementations. Ensures 100% branch coverage covering nulls, exceptions, and verifications.
---

# JCB JUnit Generator

Generate robust, self-contained JUnit 5 test classes for Spring service implementations with 100% branch coverage.

## Rules
- **Annotations**: `@SpringBootTest(classes = { <TargetClass>Test.class })`, `@TestMethodOrder(OrderAnnotation.class)`. Use `@InjectMocks` on target, `@Mock` on dependencies.
- **Static Imports**: JUnit 5 assertions (`assertEquals`, `assertThrows`, `assertNull`) and Mockito (`when`, `verify`, `times`, `never`, `any`).
- **Required Test Methods**:
  1. **Happy Path**: Successful execution and state verification.
  2. **Null Inputs**: Graceful handling of null DTOs/IDs.
  3. **Branch Paths**: Distinct tests for all `if` and `else` branches.
  4. **Exceptions**: Validations or repository failures throwing expected exceptions.
  5. **Verifications**: Verify exact invocation counts (`times(1)`, `never()`).

## JCB Tools Integration
- Analyze dependencies: `mcp_jcb_search-symbols`, `mcp_jcb_search-files`.
- Read implementation: `mcp_jcb_get-file-context`.
- Inspect call hierarchy: `mcp_jcb_get-call-hierarchy`.
- Stage test class: `mcp_jcb_stage-files`.

## Boundaries
- Output complete test class ready to save in `src/test/java/...`.
- No empty test methods or TODO comments. Clean setup per test method.
- Revert to verbose testing if user says `stop jcb-junit`.
