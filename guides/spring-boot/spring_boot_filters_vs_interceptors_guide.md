# Spring Boot Architecture: Filters vs. Interceptors Guide

Imagine a medieval fortress with two distinct tiers of defense guarding the king’s treasury. The outer sentry stationed at the drawbridge inspects all approaching visitors to verify legitimate identification before granting entry onto castle grounds. Once inside, an inner guard stationed directly outside the vault door verifies whether the visitor holds the specific security clearance required to inspect individual treasures.

In Spring Boot web application architecture, this two-tier defense pattern perfectly illustrates the operational boundary between **Filters** and **Interceptors**:

```text
[ Incoming HTTP Request ]
           |
           v
+-----------------------------------------------------------------------+
|  Servlet Container Boundary (Tomcat / Jetty / Undertow)               |
|  [ Filter 1: CORS ] -> [ Filter 2: Rate Limit ] -> [ Filter 3: Auth ] |  <-- The Outer Guards
+-----------------------------------------------------------------------+
           |
           v (Dispatched to Spring DispatcherServlet)
+-----------------------------------------------------------------------+
|  Spring MVC Framework Boundary                                        |
|  [ Interceptor: Auditing ] -> [ Interceptor: Role Authorization ]     |  <-- The Inner Guards
+-----------------------------------------------------------------------+
           |
           v
[ Target Controller / Handler Method ]
```

---

## 1. Architectural Comparison Summary

```text
+------------------------+---------------------------------------+---------------------------------------+
| Architectural Vector   | Filters (`javax.servlet.Filter`)      | Interceptors (`HandlerInterceptor`)   |
+------------------------+---------------------------------------+---------------------------------------+
| Execution Layer        | Servlet Container (Tomcat / Jetty)    | Spring MVC (`DispatcherServlet`)      |
| Context Access         | Raw HTTP Request & Response streams   | Spring ApplicationContext & Handlers  |
| Granularity            | URL pattern matching (`/api/*`)       | Exact HandlerMethod & Annotations     |
| Primary Domain         | Authentication, CORS, Rate Limiting   | Role Authorization, Auditing, Model   |
+------------------------+---------------------------------------+---------------------------------------+
```

---

## 2. Filters: The Outer Guards (Servlet Container Boundary)

Filters execute before requests cross the Spring MVC framework threshold. Because they operate at the raw Servlet Container specification layer, they are perfect for global security checks, payload decompression, and early request termination before Spring allocates internal resources.

```text
[ Raw Request ] ---> doFilter() ---> [ Valid Bearer JWT? ] ---> (Yes) ---> chain.doFilter()
                                            |
                                          (No) ---> Forcibly Terminated (401 Unauthorized)
```

### 🔐 Exemplar: `JwtAuthenticationFilter`

```java
package com.example.demo.security.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter implements Filter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String authHeader = httpRequest.getHeader("Authorization");

        // 1. Validate header presence and Bearer scheme
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\": \"Missing or invalid Bearer token\"}");
            return; // Early termination: Request never reaches Spring MVC!
        }

        // 2. Extract and validate JWT payload
        String token = authHeader.substring(7);
        if (!jwtService.isValid(token)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\": \"Expired or tampered token\"}");
            return;
        }

        // 3. Attach authenticated user role to raw request context for downstream Interceptors
        httpRequest.setAttribute("userRole", jwtService.extractRole(token));
        httpRequest.setAttribute("userId", jwtService.extractUserId(token));
        
        // 4. Pass execution control to the next filter in the pipeline
        chain.doFilter(request, response);
    }
}
```

---

## 3. Interceptors: The Inner Guards (Spring MVC Boundary)

Interceptors execute inside the Spring `DispatcherServlet`. Unlike Filters, Interceptors have complete visibility into the target Spring Controller method (`HandlerMethod`), parameter annotations, and active Spring Beans.

```text
[ Dispatched Request ] ---> preHandle() ---> Inspects @RequiresRole ---> (Role Match?) ---> Controller
                                                                               |
                                                                             (No) ---> 403 Forbidden
```

### 🛡️ Exemplar: `RoleAuthorizationInterceptor`

```java
package com.example.demo.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 1. Verify target handler is an actual Spring Controller method (skip static resources)
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 2. Reflectively inspect target method for custom @RequiresRole authorization annotation
        RequiresRole requiredRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiredRole == null) {
            return true; // No explicit role required; allow execution
        }

        // 3. Retrieve authenticated role attached earlier by the outer JwtAuthenticationFilter
        String userRole = (String) request.getAttribute("userRole");
        
        // 4. Evaluate authorization clearance
        if (userRole == null || !userRole.equals(requiredRole.value())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\": \"Forbidden: Insufficient role clearance\"}");
            return false; // Forcibly halts Controller execution
        }

        return true; // Access granted! Controller method is invoked.
    }
}
```

#### Custom Annotation Target (`RequiresRole.java`)
```java
package com.example.demo.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    String value();
}
```

---

## 4. Architectural Decision Matrix: When to Use What?

```text
+-----------------------------------+-----------------------------------+
| Use Filters For:                  | Use Interceptors For:             |
+-----------------------------------+-----------------------------------+
| [x] Authentication (JWT / API Key)| [x] Role Authorization (RBAC)     |
| [x] CORS Header Negotiation       | [x] Domain Rule Validation        |
| [x] Rate Limiting / Early Drop    | [x] User Action Auditing          |
| [x] Request Payload Decompression | [x] Execution Latency Monitoring  |
| [x] Global Security Headers (HSTS)| [x] Model Attribute Injection     |
+-----------------------------------+-----------------------------------+
```

### 🚨 Architectural Traps to Avoid
1. **The DB Lookup Filter Trap:** Never execute heavy database queries inside a Filter. Because Filters execute synchronously on every incoming socket connection, slow I/O inside a filter instantly exhausts Tomcat connection pools.
2. **The CORS Interceptor Trap:** Never configure CORS validation inside an Interceptor. Browser pre-flight `OPTIONS` requests are handled by the container before Spring MVC routing; placing CORS checks in an Interceptor causes pre-flight failures.

---

## 5. Registration Pipeline Configuration

To activate Interceptors, register them inside a Spring `WebMvcConfigurer` configuration bean.

```java
package com.example.demo.config;

import com.example.demo.security.interceptor.RoleAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final RoleAuthorizationInterceptor roleInterceptor;

    public WebSecurityConfig(RoleAuthorizationInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enforce role authorization across all API endpoints
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/public/**");
    }
}
```

---

## 6. Summary Verification Checklist

```text
[ ] 1. Auth Boundary    : Enforce JWT validation in a Filter; enforce Role checks in an Interceptor.
[ ] 2. Pre-flight Checks: Verify CORS headers and rate limiting execute at the Filter layer.
[ ] 3. Handler Casting  : Ensure Interceptor preHandle verifies handler instanceof HandlerMethod.
[ ] 4. Registration Path: Register Interceptors inside WebMvcConfigurer with explicit path patterns.
```
