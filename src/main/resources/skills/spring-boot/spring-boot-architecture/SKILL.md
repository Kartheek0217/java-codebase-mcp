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

## 3. REST API Design & Exception Handling
- **HTTP Status Mapping**: Map responses to standard HTTP status codes (e.g., `201 Created`, `200 OK`, `400 Bad Request`, `404 Not Found`, `409 Conflict`).
- **Global Handler**: Handle errors globally via `@RestControllerAdvice` and `@ExceptionHandler`. Avoid local controller-level try/catch blocks for domain failures.
- **Domain Exceptions**: Design domain exceptions extending unchecked `RuntimeException` so `@Transactional` automatically triggers rollback on failures.
- **Fallback Sanitization**: Capture `Exception.class` to log the trace internally but return a sanitized JSON payload to clients without raw stack details.
  ```java
  @RestControllerAdvice
  public class GlobalExceptionHandler {
      @ExceptionHandler(UserNotFoundException.class)
      public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND)
                  .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
      }
      @ExceptionHandler(Exception.class)
      public ResponseEntity<ErrorResponse> handleFallback(Exception ex) {
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                  .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "Unexpected system error."));
      }
  }
  ```

## 4. Production-Ready Operations & Serialization
- **Serialization Safety**: Customize the Jackson `ObjectMapper` using `Jackson2ObjectMapperBuilderCustomizer` to omit null properties, support the `JavaTimeModule`, and disable writing dates as timestamps.
- **DTO Versioning**: Version schemas explicitly using produces media types (`application/vnd.api.v1+json`) to prevent breaking changes on legacy clients.
- **Observability**: Expose actuator `prometheus` metrics, register custom business counters via Micrometer `MeterRegistry`, and set up OpenTelemetry request tracing.
- **Context-Aware Logging**: Apply servlet `Filter` MDC tags to tie unique correlation or trace IDs to all request log messages. Use Logstash JSON encoder for indexable logs.
- **Self-Healing Probes**: Expose Kubernetes liveness and readiness states via Actuator, and implement custom database connection indicators under the readiness probe.
- **Layered JARs**: Configure the Maven plugin's `layers` feature to enable Docker build cache optimizations.

## 5. Verification Checklist
- [ ] Entity models do not leak past Service boundary.
- [ ] Domain events with side effects use `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- [ ] REST API utilizes appropriate HTTP status codes.
- [ ] Exception handler uses `@RestControllerAdvice` rather than `@ControllerAdvice` for REST APIs.
- [ ] Domain exceptions extend `RuntimeException` to enable automatic transaction rollback.
- [ ] Fallback handler (`Exception.class`) masks raw traces and class details from client output.
- [ ] Jackson config disables writing dates as timestamps and excludes null properties from payload outputs.
- [ ] Micrometer Prometheus and health actuator endpoints are enabled and secured.
- [ ] MDC request trace and correlation IDs are printed in logs and returned as headers.
- [ ] Kubernetes liveness and readiness state actuator paths are enabled.
- [ ] Maven Spring Boot build-image plugin configuration specifies `<layers><enabled>true</enabled></layers>`.
