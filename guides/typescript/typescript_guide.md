---
name: ts-architecture
description: >
  Enterprise TypeScript architecture, evaluating type vs interface, declaration merging risks, and immutability invariants.
---

# Enterprise TypeScript Architecture Guide

This guide establishes standard architectural patterns for TypeScript data modeling and contract definitions: evaluating structural contracts (`type` vs. `interface`), managing extensibility, and enforcing declaration immutability across large engineering teams.

---

## 1. Architectural Comparison Matrix

In modern TypeScript engineering, defining custom structural contracts is accomplished using either `type` aliases or `interface` declarations. While their syntactic capabilities overlap significantly when defining basic object shapes, their underlying compile-time behavior differs fundamentally.

```text
+-------------------------+-----------------------------------+-----------------------------------+
| Feature                 | Type (`type`)                     | Interface (`interface`)           |
+-------------------------+-----------------------------------+-----------------------------------+
| Syntax                  | Uses assignment (`=`) operator    | Declaration block (no `=`)        |
| Extensibility           | Intersection (`&`) & Union (`|`)  | Inheritance (`extends` keyword)   |
| OOP Compatibility       | Limited (cannot implement union)  | Native (`implements` keyword)     |
| Declaration Merging     | Strictly Prohibited (Immutable)   | Active (Auto-merges same names)   |
| Compiler Performance    | Identical (0% difference)         | Identical (0% difference)         |
+-------------------------+-----------------------------------+-----------------------------------+
```

---

## 2. Structural Syntax & Extensibility

```typescript
// Type Definition (Assignment =)
type TypePerson = {
  name: string;
  age: number;
};

// Interface Definition (Declaration Block)
interface InterfacePerson {
  name: string;
  age: number;
}
```

### Interface Inheritance (`extends`)
Interfaces mimic traditional object-oriented class hierarchies by extending other interfaces.
```typescript
interface Job { jobTitle: string; }
interface ExtendedPerson extends Job { name: string; age: number; }
```

### Type Composition (Intersection `&` and Union `|`)
Types achieve rich structural composition through intersection and union operators, providing immense functional flexibility that standalone interfaces cannot directly replicate.
```typescript
// Composing types via Intersection (&)
type CompositePerson = { name: string; age: number; } & { jobTitle: string };

// Expressing Union types (|) - Impossible with standalone interfaces
type Status = "idle" | "loading" | "success" | "error";
type ApiResponse<T> = { data: T; status: Status } | { error: string; status: "error" };
```

---

## 3. Declaration Merging: The Immutability Invariant

The most critical behavioral divergence between interfaces and types is **Declaration Merging**. When the TypeScript compiler encounters multiple `interface` definitions sharing the exact same identifier within the same scope, it automatically merges their properties.

### Library Extension (When Declaration Merging is Beneficial)
Declaration merging provides an elegant mechanism for augmenting third-party library types without modifying source packages.
```typescript
// External library contract (e.g., @auth/core)
interface Session { user: { name: string; }; }

// In application code: Augmenting the external contract
interface Session { tenantId: string; }
```

### Enterprise Danger (Unintended Collisions & Silent Overwrites)
While declaration merging is highly beneficial for public NPM package authors, it introduces dangerous vulnerabilities inside enterprise application codebases:
1. **Unintended Silent Overwrites:** If two independent feature modules inadvertently declare an `interface User` with differing property signatures, the compiler merges them silently without throwing a duplicate identifier error.
2. **Unsafe Class Merging:** The compiler does not verify property initialization for dynamically merged declaration properties on classes, causing unexpected `undefined` runtime exceptions in production.

### Type Safety via Immutability
Type aliases are completely **immutable** after definition. Attempting to re-declare an existing type identifier instantly halts compilation with a fatal error:
```typescript
type AppConfig = { timeoutMs: number };

// ❌ Compiler Error: Duplicate identifier 'AppConfig'. Instantly caught during CI build!
type AppConfig = { retries: number };
```

---

## 4. Architectural Decision Matrix

```text
               [ What are you engineering? ]
                             |
         +-------------------+-------------------+
         |                                       |
[ External NPM Package ]              [ Enterprise Application ]
         |                                       |
         v                                       v
   Use `interface`                          Use `type`
(Allows consumers to augment          (Enforces immutability, prevents
 types via declaration merging)        unintended declaration collisions)
```

### Summary Recommendations
- **Default strictly to `type` aliases** for all standard application data modeling, API payloads, state slices, and union structures.
- **Use `interface` only** when writing publishable NPM library contracts intended for consumer extension or when explicitly designing OOP class hierarchies using `implements`.

---

## 5. Verification Checklist

```text
[ ] 1. Enforce Type Default : Verify application DTOs and state models utilize `type` aliases.
[ ] 2. Restrict Interfaces  : Confine `interface` usage strictly to external module augmentations or OOP contracts.
[ ] 3. Prevent Collisions   : Rely on `type` immutability to guarantee CI/CD catches duplicate identifier definitions.
[ ] 4. Leverage Unions      : Utilize union types (`|`) for robust discriminated state modeling (`status: "loading" | "success"`).
```
