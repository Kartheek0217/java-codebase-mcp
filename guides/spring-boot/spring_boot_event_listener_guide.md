# Spring Boot Event-Driven Architecture Guide (`@EventListener`)

In monolithic enterprise applications, executing secondary business logic (such as audit logging, email notifications, or cache invalidation) directly inside core transaction workflows introduces severe architectural vulnerabilities. Tightly coupling domain services increases request latency, creates complex dependency webs, and risks failing entire business transactions if a non-critical secondary service (like an SMTP mail server) experiences an outage.

Spring Framework's **Event-Driven Architecture**, powered by `ApplicationEventPublisher` and `@EventListener`, resolves these vulnerabilities by implementing an in-memory **Observer Design Pattern**. By broadcasting decoupled event objects across the Spring application container, core workflows complete instantly while autonomous listener beans react synchronously or asynchronously.

```text
+---------------------------------------------------------------------------------------+
|                       Architectural Flow: Monolithic Coupling vs. Event Decoupling    |
+---------------------------------------------------------------------------------------+
| ❌ Tightly Coupled Monolith (Fragile & Slow):                                         |
| [ Update Score Controller ] ---> Calls AuditService.log()      (Sync I/O)             |
|                             ---> Calls EmailService.send()     (Network I/O - Danger!)|
|                             ---> Calls RiskEngine.notify()     (RPC Call)             |
|                                                                                       |
| ✅ Decoupled Event-Driven Flow (Resilient & Lightning Fast):                          |
| [ Update Score Controller ] ---> ApplicationEventPublisher.publishEvent(event)        |
|                                         |                                             |
|                                         +---> [ AuditLogListener ]     (Synchronous)  |
|                                         +---> [ EmailListener ]        (@Async Thread)|
|                                         +---> [ RiskEngineListener ]   (@Async Thread)|
+---------------------------------------------------------------------------------------+
```

---

## 1. Architectural Comparison

```text
+------------------------+---------------------------------------+---------------------------------------+
| Architectural Vector   | Tightly Coupled Service Invocations   | Event-Driven (`@EventListener`)       |
+------------------------+---------------------------------------+---------------------------------------+
| Dependency Management  | Circular dependency risks; high wiring| Zero direct coupling; pure POJO events|
| Transaction Boundaries | Monolithic shared transaction context | Configurable (`@TransactionalEvent...`)|
| Execution Latency      | Additive: Core execution + all alerts | Instant: Secondary tasks offloaded    |
| Fault Isolation        | Failure in alert halts main workflow  | Isolated: Alert failures isolated     |
+------------------------+---------------------------------------+---------------------------------------+
```

---

## 2. Complete Domain Implementation: Credit Score Analysis

The following production-grade implementation illustrates decoupling a credit score modification workflow across distinct auditing and email notification sub-domains.

### 📦 1. Immutable Domain Event (`CreditScoreChangedEvent.java`)

Modern Spring events do not require extending legacy Spring classes (`ApplicationEvent`). Any standard Java POJO or record can serve as an event payload.

```java
package com.example.creditscore.events;

public final class CreditScoreChangedEvent {
    private final String customerId;
    private final int oldScore;
    private final int newScore;

    public CreditScoreChangedEvent(String customerId, int oldScore, int newScore) {
        this.customerId = customerId;
        this.oldScore = oldScore;
        this.newScore = newScore;
    }

    public String getCustomerId() { return customerId; }
    public int getOldScore() { return oldScore; }
    public int getNewScore() { return newScore; }
}
```

---

### 📢 2. Event Publisher Service (`CreditScoreService.java`)

```java
package com.example.creditscore.service;

import com.example.creditscore.events.CreditScoreChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditScoreService {

    private final ApplicationEventPublisher publisher;

    public CreditScoreService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional
    public void updateCreditScore(String customerId, int newScore) {
        // 1. Retrieve existing domain state
        int oldScore = 720; // Simulated database lookup

        // 2. Perform primary business mutation
        System.out.println("[PRIMARY WORKFLOW] Credit score updated in database for: " + customerId);

        // 3. Broadcast domain event to the Spring application container
        CreditScoreChangedEvent event = new CreditScoreChangedEvent(customerId, oldScore, newScore);
        publisher.publishEvent(event);
    }
}
```

---

### 🛡️ 3. Synchronous Listener: Audit Logging (`AuditLogListener.java`)

Synchronous listeners execute immediately within the same calling thread and transaction boundary as the event publisher.

```java
package com.example.creditscore.listeners;

import com.example.creditscore.events.CreditScoreChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditLogListener {

    // Executes synchronously on the primary HTTP worker thread
    @EventListener
    public void handleCreditScoreChange(CreditScoreChangedEvent event) {
        System.out.printf("[SYNC AUDIT LISTENER] Customer %s credit score transitioned from %d to %d.%n",
                event.getCustomerId(), event.getOldScore(), event.getNewScore());
    }
}
```

---

### ✉️ 4. Asynchronous Listener: Email Notifications (`EmailNotificationListener.java`)

Asynchronous listeners execute on an isolated thread pool. If the SMTP connection times out, the primary database transaction remains entirely unaffected.

```java
package com.example.creditscore.listeners;

import com.example.creditscore.events.CreditScoreChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationListener {

    // @Async instructs Spring to execute this handler on a background TaskExecutor thread
    @Async
    @EventListener
    public void sendCreditScoreChangeNotification(CreditScoreChangedEvent event) {
        System.out.printf("[ASYNC EMAIL LISTENER] Dispatching alert email for customer %s. (Thread: %s)%n",
                event.getCustomerId(), Thread.currentThread().getName());
    }
}
```

---

### ⚙️ 5. Application Configuration (`CreditScoreApplication.java`)

To activate `@Async` listener processing, `@EnableAsync` must be explicitly declared on a configuration class.

```java
package com.example.creditscore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // Mandatory to enable background TaskExecutor dispatching for @Async listeners
public class CreditScoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditScoreApplication.class, args);
    }
}
```

---

### 🌐 6. Rest Controller Endpoint (`CreditController.java`)

```java
package com.example.creditscore.controller;

import com.example.creditscore.service.CreditScoreService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credit")
public class CreditController {

    private final CreditScoreService creditScoreService;

    public CreditController(CreditScoreService creditScoreService) {
        this.creditScoreService = creditScoreService;
    }

    @PutMapping("/update/{customerId}/{newScore}")
    public String updateScore(@PathVariable String customerId, @PathVariable int newScore) {
        creditScoreService.updateCreditScore(customerId, newScore);
        return "Credit score transition committed. Asynchronous events broadcasted successfully.";
    }
}
```

---

## 3. Real-Time Execution Diagnostics

Executing `PUT /credit/update/CUST123/640` produces the following structured output:

```text
[PRIMARY WORKFLOW] Credit score updated in database for: CUST123
[SYNC AUDIT LISTENER] Customer CUST123 credit score transitioned from 720 to 640.
[ASYNC EMAIL LISTENER] Dispatching alert email for customer CUST123. (Thread: task-1)
```

```text
[ Main HTTP Worker Thread (http-nio-8080-exec-1) ]
  ---> Updates DB
  ---> Invokes AuditLogListener (Synchronous execution inside same thread)
  ---> Exits and returns HTTP 200 OK to client!

[ Isolated Background Thread Pool (task-1) ]
  ---> Invokes EmailNotificationListener concurrently in background
```

---

## 4. Advanced Production Patterns: `@TransactionalEventListener`

When broadcasting events from within a database transaction (such as saving an entity), standard `@EventListener` methods execute immediately—even before the primary transaction has committed to disk. If the transaction rolls back due to a subsequent SQL constraint error, an email notification may have already been dispatched.

To guarantee that secondary side effects execute *only* when the database transaction successfully commits, replace `@EventListener` with `@TransactionalEventListener`.

```java
package com.example.creditscore.listeners;

import com.example.creditscore.events.CreditScoreChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CacheInvalidationListener {

    // Ensures listener executes only after the primary database transaction successfully commits
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictCustomerCache(CreditScoreChangedEvent event) {
        System.out.println("[AFTER COMMIT LISTENER] Evicting distributed Redis cache for: " + event.getCustomerId());
    }
}
```

```text
+-----------------------------------+---------------------------------------------------+
| TransactionPhase                  | Architectural Execution Hook                      |
+-----------------------------------+---------------------------------------------------+
| `AFTER_COMMIT` (Default)          | Executes immediately upon successful DB commit.   |
| `AFTER_ROLLBACK`                  | Executes only if the primary transaction aborts.  |
| `BEFORE_COMMIT`                   | Executes right before transaction flushes to disk.|
| `AFTER_COMPLETION`                | Executes regardless of commit/rollback outcome.   |
+-----------------------------------+---------------------------------------------------+
```

---

## 5. Architectural Trap: Thread Pool Exhaustion

By default, Spring Boot's `@Async` annotation delegates execution to an auto-configured `ThreadPoolTaskExecutor`. If event publishing rates surge significantly (e.g. processing 10,000 credit updates per second), unbounded asynchronous event queues can instantly exhaust JVM heap memory.

> [!WARNING]  
> Always configure a bounded asynchronous executor pool in `application.properties` to enforce backpressure during traffic spikes:

```properties
# Spring Boot Virtual / Task Thread Pool Bounded Configuration
spring.task.execution.pool.core-size=10
spring.task.execution.pool.max-size=50
spring.task.execution.pool.queue-capacity=1000
spring.task.execution.thread-name-prefix=event-worker-
```

---

## 6. Summary Verification Checklist

```text
[ ] 1. Event Immutability: Declare all event POJO attributes as private final to prevent mutation.
[ ] 2. Async Activation  : Ensure @EnableAsync is explicitly declared on an app configuration bean.
[ ] 3. Thread Boundaries : Verify @Async listeners execute on isolated background worker threads.
[ ] 4. Commit Guarantees : Use @TransactionalEventListener for actions dependent on DB persistence.
[ ] 5. Pool Backpressure : Configure bounded task execution queue capacities in application properties.
```
