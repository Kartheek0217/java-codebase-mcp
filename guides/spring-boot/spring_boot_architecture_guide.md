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

## 3. Architecture Verification

```text
[ ] 1. Event Decoupling  : Audit `@EventListener` annotations to ensure transactional operations use `@TransactionalEventListener`.
[ ] 2. Defense Layering  : Verify security/CORS logic is positioned in Filters, while Controller metadata checks reside in Interceptors.
```
