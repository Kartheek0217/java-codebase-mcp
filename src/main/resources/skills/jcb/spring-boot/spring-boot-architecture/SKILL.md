---
name: spring-boot-architecture
description: Multi-layered architecture, DTO mapping, REST standards, and transactional domain events.
---

# Spring Boot Architecture

## 1. Layered Architecture
- **Invariants**:
  - Controllers only communicate with Services. Services handle transactions/logic. Repositories manage DB access.
  - Entities must not escape Service boundary. Map Entities to immutable records (DTOs) at Service boundary.

## 2. Transactional Domain Events
- `@TransactionalEventListener`: Use `AFTER_COMMIT` to ensure events are published only if the DB transaction succeeds.
  ```java
  @Transactional
  public void createOrder(Order o) {
      repository.save(o);
      publisher.publishEvent(new OrderCreatedEvent(o));
  }
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleOrderCreated(OrderCreatedEvent event) {
      emailService.sendEmail(event.userId());
  }
  ```

## 3. REST API Design
- Use proper HTTP statuses: `201 Created`, `200 OK`, `400 Bad Request`, `404 Not Found`.
- Handle errors globally via `@RestControllerAdvice` and `@ExceptionHandler`.

## 4. Verification Checklist
- [ ] Entity models do not leak past Service boundary.
- [ ] Domain events with side effects use `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- [ ] REST API utilizes appropriate HTTP status codes.
