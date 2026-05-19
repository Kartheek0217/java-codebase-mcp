---
name: spring-boot-utilities
description: >
  Designing robust utility classes and cataloging the 62 built-in Spring Framework utilities.
---

# Spring Boot Utilities & Helper Architecture Guide

This guide establishes standards for designing custom utility classes and catalogs the 62 robust, built-in utility classes provided natively by the Spring Framework.

---

## 1. Senior Utility Class Design Invariants

Utility classes in Java represent static, stateless helper functions. When engineered improperly, they introduce reflection overhead, unexpected instantiations, and subclassing vulnerabilities.

```java
// ✅ Production-Grade Utility Class Architecture
public final class SecurityDigestUtils {

    // 1. Enforce strict private constructor throwing AssertionError to prevent reflection instantiations
    private SecurityDigestUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    // 2. All methods must be public static and completely stateless (pure functions)
    public static String sha256Hex(String plaintext) {
        return DigestUtils.sha256Hex(plaintext);
    }
}
```

### Architectural Invariants
1. **Final Class Enforcement:** Always mark utility classes `final` to explicitly prohibit inheritance.
2. **Prevent Instantiation:** The default no-arg constructor must be explicitly overridden as `private` and throw an `AssertionError`.
3. **Stateless Guarantee:** Never maintain mutable static fields inside utility classes.

---

## 2. The 62 Built-In Spring Utilities (Zero Dependency Rule)

Senior Spring Boot engineers leverage Spring's rich internal utility suite before introducing redundant external third-party libraries (e.g., Apache Commons, Guava).

```text
+---------------------------------------------------------------------------------------+
|                       Categorized Spring Framework Utilities Suite                    |
+---------------------------------------------------------------------------------------+
| 💡 Core Utilities (`org.springframework.util`)                                        |
|   ├── ObjectUtils (null/empty evaluations, array conversions)                         |
|   ├── StringUtils (text truncation, tokenization, whitespace manipulation)            |
|   ├── CollectionUtils (collection checking, unmodifiable adapters)                    |
|   ├── FileCopyUtils / StreamUtils (high-speed I/O stream copies)                    |
|   ├── NumberUtils (precision parsing, type conversions)                               |
|   └── PatternMatchUtils (glob matching without regex overhead)                        |
|                                                                                       |
| 🔒 Cryptographic & Hashing (`org.springframework.util.DigestUtils`)                   |
|   └── DigestUtils (MD5, SHA-1, SHA-256 byte/hex transformations)                      |
|                                                                                       |
| 🌐 Web & HTTP (`org.springframework.web.util`)                                        |
|   ├── UriComponentsBuilder (type-safe URI construction, encoding)                     |
|   ├── HtmlUtils (HTML string escaping/unescaping)                                     |
|   ├── JavaScriptUtils (JS string literal escaping)                                    |
|   └── WebUtils (cookie extraction, session attribute evaluation)                      |
|                                                                                       |
| ⚙️ Reflection & AOP (`org.springframework.util.ReflectionUtils`)                      |
|   ├── ReflectionUtils (exception-safe field/method inspection)                        |
|   ├── ClassUtils (classloader evaluations, proxy inspection)                          |
|   └── AopUtils (CGLIB vs JDK dynamic proxy validation)                                |
+---------------------------------------------------------------------------------------+
```

### High-Speed I/O Helper (`StreamUtils`)
```java
// High-speed stream copying using built-in Spring buffers
public byte[] readPayload(InputStream in) throws IOException {
    return StreamUtils.copyToByteArray(in);
}
```

### Type-Safe URI Construction (`UriComponentsBuilder`)
```java
URI uri = UriComponentsBuilder.fromHttpUrl("https://api.enterprise.com")
    .path("/v1/orders/{id}")
    .queryParam("verbose", true)
    .buildAndExpand(orderId)
    .encode()
    .toUri();
```

---

## 3. Verification Checklist

```text
[ ] 1. Utility Architecture : Verify all helper classes are marked `final` with private constructors throwing AssertionError.
[ ] 2. Redundancy Audit     : Remove external libraries (Apache Commons) where built-in Spring utilities satisfy requirements.
```
