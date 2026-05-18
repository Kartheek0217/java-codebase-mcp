---
name: caveman-junit
description: >
  Ultra-compressed JUnit test generator. Creates self-contained, robust JUnit 5 unit test classes
  for Spring service implementation classes. Ensures 100% branch coverage by testing all if/else
  conditions, null inputs, exceptions, and happy paths. Uses @SpringBootTest, @Mock, @InjectMocks,
  and Mockito verifications. Use when user says "generate tests", "write unit test", "/junit",
  or invokes /caveman-junit.
---

Generate self-contained JUnit 5 unit test classes terse and complete. Cover 100% branch logic (all if/else paths, nulls, exceptions). No fluff.

## Rules

**Structure & Annotations:**
- Use JUnit 5 and Mockito static imports (`assertEquals`, `assertNotNull`, `assertNull`, `assertThrows`, `when`, `verify`, `times`, `never`, `any`).
- Class annotations exactly:
```java
@SpringBootTest(classes = { <TargetClass>Test.class })
@TestMethodOrder(OrderAnnotation.class)
```
- Use `@InjectMocks` on target service implementation.
- Use `@Mock` on all dependencies (repositories, mappers, external services).

**Test Coverage Requirements:**
Every service impl method must have distinct `@Test` methods covering:
1. **Happy path / successful execution:** mock repository/mapper calls, verify non-null return or expected state.
2. **Null inputs:** verify method handles `null` DTOs or IDs gracefully (returns null or throws expected exception).
3. **Branch paths (if/else logic):** mock conditions for both `if` and `else` branches (e.g., existing entity vs new entity, active vs inactive status mapping).
4. **Exceptions & validations:** mock repository returning duplicate records or failing validations; use `assertThrows`.
5. **Deletions / State modifications:** verify repository `save()` or `delete()` is called exact number of times (`times(1)` vs `never()`).

**Code Style:**
- No empty test methods or placeholder TODOs.
- Clean setup within test methods (DTOs, Entities, Mocks).
- Exact assertions (`assertEquals`, `assertNotNull`, `assertNull`).

## Example Structure

```java
package com.intellect.overdraftms.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = { UnitofMeasureServiceImplTest.class })
@TestMethodOrder(OrderAnnotation.class)
class UnitofMeasureServiceImplTest {

    @InjectMocks
    private UnitofMeasureServiceImpl unitofMeasureServiceImpl;

    @Mock
    private UnitofMeasureRepository unitofMeasureRepository;

    @Mock
    private UnitofMeasureMapper unitofMeasureMapper;

    @Test
    void testSave() throws OverDraftBusinessException { ... }

    @Test
    void testSaveWithNullDto() throws OverDraftBusinessException { ... }

    @Test
    void testSaveShouldThrowExceptionWhenDuplicateNameFound() { ... }
}
```

## Boundaries

Output the complete, self-contained test class ready to save to `src/test/java/...`. "stop caveman-junit" or "normal mode": revert to standard verbose testing style.
