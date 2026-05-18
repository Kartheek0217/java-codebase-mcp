# Java Clean Code & Branching Architecture Guide

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

---

## 1. Core Refactoring Patterns for Branching

```text
+-------------------------------------+----------------------------------------------------+
| Architectural Pattern               | Primary Best-Use Case                              |
+-------------------------------------+----------------------------------------------------+
| Polymorphism                        | Diverging structural behaviors across domain models|
| Enums with Behavior                 | Fixed, finite sets of strategies (e.g., pricing)   |
| Map of Functions                    | Data-driven command dispatching and routing        |
| Optional Pipelines                  | Eliminating null checks and safe traversal         |
| Guard Clauses                       | Validations and eliminating nested indentation     |
| Strategy Pattern                    | Decoupled, dynamic business calculation rules      |
| Modern Switch Expressions           | Clean, exhaustive matching without fall-through    |
| Pattern Matching                    | Clean runtime type evaluation and casting          |
| Rule Engines / Pipelines            | Open-ended, overlapping complex business logic     |
+-------------------------------------+----------------------------------------------------+
```

### 1. Guard Clauses (Early Returns)
Invert validation checks and exit early to eliminate nested indentation.
```java
public void processOrder(Order order) {
    if (order == null || !order.isPaid()) return;
    if (order.getItems().isEmpty()) return;

    shipOrder(order); // Clean, unindented core execution
}
```

### 2. Functional Command Dispatching (Map of Functions)
Transform procedural branching into an $O(1)$ data-driven lookup table.
```java
@Service
public class ActionDispatcher {
    private final Map<String, Runnable> actions = Map.of(
        "CREATE", this::createUser,
        "UPDATE", this::updateUser,
        "DELETE", this::deleteUser
    );

    public void dispatch(String actionCode) {
        actions.getOrDefault(actionCode, () -> { throw new IllegalArgumentException("Invalid action"); }).run();
    }
}
```

### 3. Decoupled Rule Engines
When domain logic requires overlapping, toggleable, or prioritized validations, replace branching entirely with a modular rule pipeline.
```java
public interface OrderRule {
    boolean matches(Order order);
    void apply(Order order);
}

@Component
public class OrderRuleEngine {
    private final List<OrderRule> rules; // Spring injects all implementing beans

    public OrderRuleEngine(List<OrderRule> rules) { this.rules = rules; }

    public void evaluate(Order order) {
        rules.stream().filter(r -> r.matches(order)).forEach(r -> r.apply(order));
    }
}
```

---

## 2. Java Enum Architecture & High-Performance Equality

Enums provide compile-time verified singleton instances that eliminate the type-safety vulnerabilities of conventional primitive string/int constants.

```text
+---------------------------------------------------------------------------------------+
|                            JVM Enum Singleton Memory Model                            |
+---------------------------------------------------------------------------------------+
| [ Class Loader ] ---> Loads Process.class exactly once upon initialization            |
|                                                                                       |
| Memory Heap:                                                                          |
| [ Process.STARTED ]     [ Process.IN_PROGRESS ]    [ Process.COMPLETED ]              |
| (Address: 0x7A01)       (Address: 0x7A02)          (Address: 0x7A03)                  |
|                                                                                       |
| Equality Evaluation (==):                                                             |
| (var1 == Process.STARTED) ---> Directly compares pointer address 0x7A01 (Ultra-Fast!) |
+---------------------------------------------------------------------------------------+
```

### Identity Equality (`==`) vs. `.equals()`
When comparing Enum instances in business logic, utilizing the identity equality operator (`==`) is universally recommended over `.equals()`:
1. **Ultra-Fast Pointer Comparison:** Because Enums are absolute JVM singletons, reference comparison using `==` executes an instant pointer memory address comparison ($O(1)$ assembly instruction).
2. **Absolute Null-Safety:** Executing `null == ProcessStatus.STARTED` safely returns `false`. Executing `nullObj.equals(ProcessStatus.STARTED)` instantly throws a `NullPointerException`.
3. **Compile-Time Verification:** The compiler proactively rejects identity comparisons between unrelated Enum definitions (`if (Day.MONDAY == Role.ADMIN)` fails compilation).

### Enums With Embedded Behavior (Mini Strategy Pattern)
Because Enums are fully featured classes, attaching abstract methods or private fields allows constants to encapsulate rich domain logic and calculations.

```java
public enum SubscriptionPlan {
    BASIC(199) {
        @Override
        public double calculateDiscount(double price) { return price * 0.05; }
    },
    PRO(499) {
        @Override
        public double calculateDiscount(double price) { return price * 0.15; }
    };

    private final int basePrice;
    SubscriptionPlan(int basePrice) { this.basePrice = basePrice; }
    public int getBasePrice() { return basePrice; }
    public abstract double calculateDiscount(double price);
}
```

### Exhaustive Switch Expressions (Java 14+)
Modern switch expressions eliminate fall-through bugs (`break` omission) and enforce compile-time exhaustiveness without requiring `default` blocks.
```java
String statusMessage = switch (currentStatus) {
    case STARTED -> "Execution initialized";
    case IN_PROGRESS -> "Processing payload";
    case COMPLETED -> "Processing successful";
    case FAILED -> "Processing failed";
};
```

---

## 3. Clean Architecture Verification

```text
[ ] 1. Branch Elimination : Replace deeply nested if-else ladders with polymorphism or command maps.
[ ] 2. Guard Invariance   : Enforce early return guard clauses to prevent nested code blocks.
[ ] 3. Enum Equality      : Audit codebase to ensure all Enum comparisons utilize `==` instead of `.equals()`.
[ ] 4. Switch Exhaustive  : Transition legacy switch statements to modern exhaustive switch expressions.
```
