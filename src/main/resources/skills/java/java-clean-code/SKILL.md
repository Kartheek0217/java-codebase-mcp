---
name: java-clean-code
description: Refactoring branching logic, guard clauses, functional command maps, rule engines, Enum pointer equality, and switch expressions.
---

# Java Clean Code & Branching

## 1. Refactoring Branching
- **Guard Clauses**: Exit early to avoid nested `if-else` indentation.
  ```java
  public void processOrder(Order order) {
      if (order == null || !order.isPaid()) return;
      if (order.getItems().isEmpty()) return;
      shipOrder(order);
  }
  ```
- **Functional Command Map**: O(1) data-driven dispatcher instead of procedural branching.
  ```java
  private final Map<String, Runnable> actions = Map.of(
      "CREATE", this::createUser,
      "UPDATE", this::updateUser,
      "DELETE", this::deleteUser
  );
  public void dispatch(String code) {
      actions.getOrDefault(code, () -> { throw new IllegalArgumentException(); }).run();
  }
  ```
- **Rule Engine**: Decouple logic into prioritized lists of rule beans.
  ```java
  public interface OrderRule { boolean matches(Order o); void apply(Order o); }
  @Component
  public class OrderRuleEngine {
      private final List<OrderRule> rules;
      public OrderRuleEngine(List<OrderRule> rules) { this.rules = rules; }
      public void evaluate(Order o) {
          rules.stream().filter(r -> r.matches(o)).forEach(r -> r.apply(o));
      }
  }
  ```

## 2. Enums
- **Identity Equality (`==`)**: Always use `==` instead of `.equals()`.
  - Compile-time type-safety check.
  - Zero performance overhead (pointer comparison).
  - Safe from `NullPointerException`.
- **Embedded Behavior (Strategy Pattern)**:
  ```java
  public enum Plan {
      BASIC(199) { @Override public double discount(double p) { return p * 0.05; } },
      PRO(499) { @Override public double discount(double p) { return p * 0.15; } };
      private final int price;
      Plan(int price) { this.price = price; }
      public abstract double discount(double p);
  }
  ```
- **Exhaustive Switch Expressions**: Modern switch statements (Java 14+) without default blocks to enforce exhaustive matching.
  ```java
  String msg = switch (status) {
      case STARTED -> "Init";
      case IN_PROGRESS -> "Running";
      case COMPLETED -> "Done";
      case FAILED -> "Error";
  };
  ```

## 3. Verification Checklist
- [ ] Nested if-else replaced with polymorphism, maps, or rule engines.
- [ ] Guard clauses used for validations.
- [ ] Enum comparisons use `==` instead of `.equals()`.
- [ ] Switch statements transitioned to modern expressions.
