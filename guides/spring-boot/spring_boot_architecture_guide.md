---
name: spring-boot-architecture
description: >
  Enterprise Spring Boot architecture: decoupled event publishing, @TransactionalEventListener boundaries, and request defense pipelines.
---

# Spring Boot Enterprise Architecture Guide

This guide establishes strict architectural standards for Spring Boot applications: designing decoupled event-driven workflows and structuring robust two-tier request defense pipelines with Filters and Interceptors.

---

## 1. Decoupled Event-Driven Architecture (`@EventListener`)

In tight, synchronously coupled service architectures, executing secondary side-effects (e.g., sending email notifications, auditing logs, dispatching webhooks) directly inside primary transaction workflows severely inflates latency and introduces cascading failure risks.

```text
+---------------------------------------------------------------------------------------+
|                 Synchronous Monolith vs. Decoupled Event Architecture                 |
+---------------------------------------------------------------------------------------+
| ❌ Synchronous Monolith (High Latency & Cascading Failure Risk):                       |
| [ OrderService.create() ] ---> DB Save (10ms) ---> Send Email (1500ms!) ---> Commit   |
|                                                                                       |
| ✅ Decoupled Event Pipeline (@TransactionalEventListener):                             |
| [ OrderService.create() ] ---> DB Save (10ms) ---> Commit & Publish Event ---> Return |
|                                                       |                               |
|                                         Async Consumer executes Email (Isolated Pool) |
+---------------------------------------------------------------------------------------+
```

### The Transactional Event Invariant (`@TransactionalEventListener`)
When publishing domain events from within an active database transaction (`@Transactional`), standard `@EventListener` invokes the consumer immediately *before* the parent transaction commits. If the database transaction subsequently rolls back due to a SQL constraint violation, the consumer has already executed external side-effects (e.g., dispatching an email or calling Stripe).

```java
public record OrderPlacedEvent(Long orderId, String customerEmail) {}

@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;
    private final OrderRepository repository;

    @Transactional
    public Order createOrder(OrderDto dto) {
        Order order = repository.save(new Order(dto));
        // Publish event; listeners execute only upon successful DB transaction commit
        publisher.publishEvent(new OrderPlacedEvent(order.getId(), order.getCustomerEmail()));
        return order;
    }
}

@Component
public class OrderNotificationListener {
    // Executes strictly after the parent transaction commits successfully
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailClient.sendOrderConfirmation(event.customerEmail(), event.orderId());
    }
}
```

---

## 2. Request Defense Pipelines: Filters vs. Interceptors

A robust enterprise API requires two layers of defense: OS/Web container boundaries (Filters) and Spring Application Context boundaries (HandlerInterceptors).

```text
+---------------------------------------------------------------------------------------+
|                           Two-Tier Defense Architecture                               |
+---------------------------------------------------------------------------------------+
| [ Incoming HTTP Request ]                                                             |
|          |                                                                            |
|          v                                                                            |
| +-----------------------------------------------------------------------------------+ |
| | Tier 1: Servlet Filters (OncePerRequestFilter)                                    | |
| | Execute outside Spring MVC. Ideal for CORS, Rate Limiting, & Global JWT validation| |
| +-----------------------------------------------------------------------------------+ |
|          |                                                                            |
|          v                                                                            |
| +-----------------------------------------------------------------------------------+ |
| | Tier 2: Spring HandlerInterceptors                                                | |
| | Full access to HandlerMethod (@RolesAllowed, Controller metadata, Context logging)| |
| +-----------------------------------------------------------------------------------+ |
|          |                                                                            |
|          v                                                                            |
| [ Target Controller Method ]                                                          |
+---------------------------------------------------------------------------------------+
```

### 1. Tier 1 Defense: `OncePerRequestFilter`
Servlet filters execute at the Servlet container level before the Spring MVC `DispatcherServlet` is reached.
```java
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private final RateLimiter service;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (!service.isAllowed(apiKey)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return; // Reject instantly before reaching Spring MVC
        }
        chain.doFilter(request, response);
    }
}
```

### 2. Tier 2 Defense: `HandlerInterceptor`
Interceptors execute inside Spring MVC, providing access to the mapped `HandlerMethod` reflection metadata.
```java
@Component
public class PermissionVerificationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RequirePermission annotation = method.getMethodAnnotation(RequirePermission.class);
            if (annotation != null && !UserContext.hasRole(annotation.value())) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }
        }
        return true;
    }
}
```

---

## 3. Global Exception Handling & Domain Exceptions

Allowing unhandled exceptions to escape the application boundary degrades user experience, causes data loss (from abrupt client-side crashes), and leaks internal stack details (e.g., class names, package structure, and line numbers), introducing severe security vulnerabilities.

### The Default Error Handling Flow
By default, Spring Boot redirects unhandled exceptions to the `/error` path, handled by the `BasicErrorController`.
Depending on the client type:
* **Web Browsers**: Renders a generic HTML "Whitelabel Error Page".
* **API Clients (Postman/Frontend apps)**: Returns a JSON error response containing the raw stack trace.

```json
{
  "timestamp": "2026-05-19T10:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "trace": "java.lang.NullPointerException at com.app.service.UserService.findById(UserService.java:34)..."
}
```
Exposing raw traces leaks internal design details and is a production security risk.

---

### The Evolution of Exception Handling

#### 1. Controller-Level try/catch (Anti-Pattern)
A common beginner approach is catching exceptions directly in controllers.

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        try {
            User user = userService.findById(id);
            return ResponseEntity.ok(user);
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(404).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}
```
* **Why it fails to scale**:
  1. **Code Duplication**: The same catch blocks are duplicated across every controller method.
  2. **High Maintenance**: Changing the error schema requires editing dozens of endpoints.
  3. **Controller Pollution**: Controllers should manage HTTP requests, not complex error-formatting logic.

#### 2. Global Exception Handling with `@ControllerAdvice` vs `@RestControllerAdvice`
Using global advices intercepts exceptions globally before they reach the client, separating concern layers.

* **`@ControllerAdvice`**:
  Designed for traditional MVC controllers returning HTML views. Returning a raw string (e.g., `"error"`) causes Spring to attempt to resolve an `error.html` template.
  ```java
  @ControllerAdvice
  public class GlobalExceptionHandler {
      @ExceptionHandler(Exception.class)
      public String handleException() {
          return "error-page"; // Resolves to an HTML view
      }
  }
  ```
  *Note*: Returning `ResponseEntity<?>` from `@ControllerAdvice` will write to the body, but it is fragile if plain objects or strings are returned.

* **`@RestControllerAdvice`**:
  A specialized shortcut annotation combining `@ControllerAdvice` and `@ResponseBody`. It automatically serializes return values (DTOs, strings, collections) into JSON/XML payloads directly inside the HTTP response body.
  ```java
  @RestControllerAdvice
  public class GlobalExceptionHandler {
      @ExceptionHandler(Exception.class)
      public ResponseEntity<String> handleException() {
          return ResponseEntity.badRequest().body("Error occurred");
      }
  }
  ```

---

### Implementation Patterns

#### 1. Creating Semantic Domain Exceptions
Always extend `RuntimeException` rather than checked `Exception`. Spring's transaction boundary (`@Transactional`) automatically rolls back database updates only for unchecked exceptions (`RuntimeException` and `Error`) by default.

```java
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }
}

public class OrderAlreadyPaidException extends DomainException {
    public OrderAlreadyPaidException(Long orderId) {
        super("Order " + orderId + " has already been paid");
    }
}
```

#### 2. Wiring the Handler
Define a standard error payload and capture domain exceptions. Keep a catch-all `Exception.class` handler to log and sanitize unexpected system errors.

```java
public record ErrorResponse(String code, String message, Instant timestamp) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OrderAlreadyPaidException.class)
    public ResponseEntity<ErrorResponse> handleOrderAlreadyPaid(OrderAlreadyPaidException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ORDER_ALREADY_PAID", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleFallback(Exception ex) {
        // Log the actual trace internally (e.g. log.error("System exception: ", ex))
        // and return a sanitized message to the client.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected system error occurred."));
    }
}
```

#### 3. Service Layer Integration
Throw the domain exceptions using modern options mapping:

```java
@Service
public class UserService {
    private final UserRepository userRepository;

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
```

---

## 4. Safe Serialization & DTO Versioning

Serialization determines how microservices communicate (REST, Kafka, Redis, or WebSockets). Default Java serialization (`ObjectInputStream`) is highly discouraged in enterprise environments due to security vulnerabilities, version fragility, and heavy runtime reflection overhead.

#### 1. Schema-Controlled Formats & Jackson Configuration
Production systems enforce schema consistency using lightweight JSON formats or schema-controlled binary formats like Avro and Protobuf. In Spring Boot, Jackson can be customized to filter out unpopulated elements and support modern time formats.

```java
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                .serializationInclusion(JsonInclude.Include.NON_NULL) // Omit null fields to save bandwidth
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Write human-readable ISO-8601 strings
                .modulesToInstall(new JavaTimeModule()); // Full JSR-310 support
    }
}
```

#### 2. DTO Versioning for Backward Compatibility
APIs must evolve without breaking legacy clients. Exposing versioned resources using content-negotiation (media types) prevents URI clutter and ensures seamless client upgrades.

```java
// Legacy payload
public record CustomerV1(String name, String email) {}

// Extended payload
public record CustomerV2(String name, String email, String loyaltyTier) {}

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    @GetMapping(value = "/{id}", produces = "application/vnd.api.v1+json")
    public ResponseEntity<CustomerV1> getCustomerV1(@PathVariable Long id) {
        return ResponseEntity.ok(new CustomerV1("Alice", "alice@example.com"));
    }

    @GetMapping(value = "/{id}", produces = "application/vnd.api.v2+json")
    public ResponseEntity<CustomerV2> getCustomerV2(@PathVariable Long id) {
        return ResponseEntity.ok(new CustomerV2("Alice", "alice@example.com", "GOLD"));
    }
}
```

---

## 5. Cloud-Native Observability (Micrometer + OpenTelemetry)

Observability provides deep runtime insights into the JVM, database connection pools, memory utilization, and application processing metrics.

#### Actuator Setup (metrics & tracing)
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: customer-service
  tracing:
    sampling:
      probability: 1.0 # Sample all requests for absolute tracing accuracy (reduce in high-volume production)
```

#### Custom Domain Performance Metrics
```java
@Component
public class OrderMetrics {
    private final Counter processedOrders;

    public OrderMetrics(MeterRegistry registry) {
        this.processedOrders = Counter.builder("orders.processed")
                .description("Total orders successfully processed")
                .register(registry);
    }

    public void increment() {
        processedOrders.increment();
    }
}
```

---

## 6. Context-Aware Tracing (MDC Filter)

To correlate requests traversing multiple independent microservices, each request must carry a unique trace context. Storing correlation identifiers in the logging `MDC` (Mapped Diagnostic Context) attaches these IDs to every log statement.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceMDCFilter extends OncePerRequestFilter {
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incomingTraceId = request.getHeader("X-Trace-Id");
        String traceId = (incomingTraceId != null && !incomingTraceId.isBlank()) 
                ? incomingTraceId 
                : UUID.randomUUID().toString();

        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

---

## 7. Structured JSON Logging & Correlation IDs

Unstructured text logs are hard to search and analyze. Structured JSON logging transforms log entries into machine-readable payloads for centralized log collectors like Elasticsearch or Splunk.

#### 1. Logstash Encoder Dependency
Include the encoder library in `pom.xml`:
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

#### 2. Logback Configuration (`logback-spring.xml`)
```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <threadName/>
                <message/>
                <mdc/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

#### 3. Interceptor-Based Correlation Tracking
```java
@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {
    private static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String correlationId = req.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(CORRELATION_ID_KEY, correlationId);
        log.info("Request started: {} {}", req.getMethod(), req.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        MDC.clear();
    }
}
```

---

## 8. Self-Healing Probes (Liveness & Readiness)

Kubernetes monitors probes to manage application traffic and automatically restart deadlocked containers.

#### YAML Configuration
```yaml
management:
  endpoint:
    health:
      show-details: always
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```
* **Liveness Endpoint** (`/actuator/health/liveness`): Confirms the JVM is running.
* **Readiness Endpoint** (`/actuator/health/readiness`): Confirms internal dependencies (DB, Redis, Message Queues) are reachable.

#### Custom Database Connection Indicator
```java
@Component
public class DatabaseHealthIndicator extends AbstractHealthIndicator {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            builder.up().withDetail("database", "reachable");
        } catch (Exception e) {
            builder.down(e).withDetail("database", "unreachable");
        }
    }
}
```

---

## 9. Efficient Containerization (Layered JARs)

Fat JARs force Docker to rebuild and push entire dependency packages even for single-line code changes. Layered JARs divide the package into distinct segments, leveraging Docker's layer caching to optimize build pipelines.

#### Plugin Configuration in `pom.xml`
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <layers>
            <enabled>true</enabled>
        </layers>
    </configuration>
</plugin>
```

#### Multi-stage Dockerfile Example
```dockerfile
FROM eclipse-temurin:21-jre-jammy AS builder
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

---

## 10. Architecture Verification Checklist

```text
[ ] 1. Event Decoupling     : Audit `@EventListener` annotations to ensure transactional operations use `@TransactionalEventListener`.
[ ] 2. Defense Layering     : Verify security/CORS logic is positioned in Filters, while Controller metadata checks reside in Interceptors.
[ ] 3. Exception Handling   : Ensure global error interception is done via `@RestControllerAdvice` and domain exceptions extend `RuntimeException`.
[ ] 4. Trace Sanitization   : Fallback handlers (`Exception.class`) must hide raw stack traces and internal class names.
[ ] 5. Serialization Safety : Disable WRITE_DATES_AS_TIMESTAMPS and exclude null fields to guarantee clean DTO schemas.
[ ] 6. Observability Signals: Actuator Prometheus endpoints and OpenTelemetry trace contexts are configured.
[ ] 7. Log Structuredness   : Utilize JSON composite encoders and log MDC correlation IDs for requests.
[ ] 8. Self-Healing Probes  : Actuator health endpoints are exposed for Kubernetes liveness and readiness validation.
[ ] 9. Layered Packaging    : Enable Maven plugin layertools packaging to optimize Docker build layer caching.
```
