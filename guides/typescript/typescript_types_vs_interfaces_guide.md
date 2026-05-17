# TypeScript Types vs. Interfaces Guide: Architectural Comparison and Best Practices

In modern TypeScript engineering, defining custom structural contracts is accomplished using either `type` aliases or `interface` declarations. Because their basic syntax and functional capabilities overlap significantly, engineers frequently question which construct is architecturally superior.

This guide provides an exhaustive technical comparison of extensibility, object-oriented implementation, compiler performance, and declaration safety, detailing why modern codebases generally default to `type` over `interface`.

---

## 1. Architectural Overview: Core Comparison

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

## 2. Syntax & Basic Structural Definition

At a foundational level, both constructs successfully model object shapes. The syntactic difference lies in the assignment operator (`=`).

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

---

## 3. Extensibility & Composition

### Interface Inheritance (`extends`)
Interfaces mimic traditional object-oriented class hierarchies by extending other interfaces using the `extends` keyword.

```typescript
interface Job {
  jobTitle: string;
}

// Person inherits properties from Job
interface ExtendedPerson extends Job {
  name: string;
  age: number;
}

const employee: ExtendedPerson = {
  name: "John Doe",
  age: 28,
  jobTitle: "Senior TypeScript Engineer",
};
```

### Type Composition (Intersection `&` and Union `|`)
Types achieve rich composition through intersection (`&`) and union (`|`) operators. This provides immense functional flexibility that interfaces cannot directly replicate inline.

```typescript
// Composing types via Intersection (&)
type CompositePerson = {
  name: string;
  age: number;
} & { jobTitle: string };

// Expressing Union types (|) - Impossible with standalone interfaces
type Status = "idle" | "loading" | "success" | "error";
type ApiResponse<T> = { data: T; status: Status } | { error: string; status: "error" };
```

---

## 4. OOP Class Implementation (`implements`)

For codebases heavily structured around Object-Oriented Programming (OOP) paradigms (similar to Java or C# architectures), `interface` provides native compatibility with class definitions.

```typescript
interface Workable {
  doWork: () => void;
}

class SoftwareEngineer implements Workable {
  public name: string;
  public age: number;

  constructor(name: string, age: number) {
    this.name = name;
    this.age = age;
  }

  // Contract satisfaction required by Workable interface
  public doWork(): void {
    console.log("Compiling TypeScript AST...");
  }
}

const engineer = new SoftwareEngineer("Alice", 30);
engineer.doWork();
```

> [!NOTE]  
> While classes can implement type aliases representing static object shapes, a class **cannot** implement a type alias that represents a union type (`type A = B | C`).

---

## 5. Compiler Type-Checking Performance

A long-standing developer myth asserts that interfaces type-check faster than type aliases during compilation across massive enterprise codebases.

**The Reality:** Comprehensive compiler benchmarking (highlighted by TypeScript compiler experts like Matt Pocock) confirms there is exactly **ZERO difference in type-checking performance** between types and interfaces in modern TypeScript releases. The compiler transforms both constructs into identical internal intermediate representations.

---

## 6. Declaration Merging: The Double-Edged Sword

The most significant behavioral divergence between interfaces and types is **Declaration Merging**. When the TypeScript compiler encounters multiple `interface` definitions sharing the exact same identifier within the same scope, it automatically merges their property definitions into a single combined contract.

### ✅ The Good: Extending Library Contracts
Declaration merging is an elegant mechanism for augmenting external third-party library types without modifying source packages.

```typescript
// External library module definition (e.g., @auth/core)
interface Session {
  user: { name: string; email: string };
  expires: string;
}

// In your application code: Augmenting the external Session interface
interface Session {
  userId: string; // Adding custom tenant ID property
  role: "ADMIN" | "USER";
}

// The compiler successfully enforces the combined properties
const activeSession: Session = {
  user: { name: "Bob", email: "bob@example.com" },
  expires: "2026-12-31T23:59:59Z",
  userId: "usr_99812",
  role: "ADMIN",
};
```

---

### ❌ The Bad & Harmful: Unintended Collisions and Runtime Bugs

While declaration merging is highly beneficial for library authors, it introduces dangerous vulnerabilities inside standard application codebases:

1. **Unintended Silent Overwrites (Order of Precedence):** Later declarations in the compilation graph take precedence over earlier ones. If two independent feature modules inadvertently declare an `interface User` with differing property signatures, the compiler merges them silently without throwing a duplicate identifier error.
2. **Unsafe Class Merging:** The TypeScript compiler does not verify property initialization for dynamically merged declaration properties on classes. This easily causes unexpected `undefined` runtime exceptions in production.

#### Type Safety via Immutability
Type aliases are completely **immutable** after definition. Attempting to re-declare an existing type identifier instantly halts compilation with a fatal error:

```typescript
type AppConfig = { timeoutMs: number };

// ❌ Compiler Error: Duplicate identifier 'AppConfig'.
type AppConfig = { retries: number };
```

This strict immutability makes `type` significantly safer and more predictable across large distributed development teams.

---

## 7. Architectural Decision Matrix

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
- Default strictly to `type` aliases for all standard application data modeling, API payloads, state slices, and union structures.
- Use `interface` only when writing publishable NPM library contracts intended for consumer extension or when explicitly designing OOP class hierarchies using `implements`.
