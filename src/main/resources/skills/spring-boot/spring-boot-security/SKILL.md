---
name: spring-boot-security
description: Authentication, authorization, cors, and secure headers.
---

# Spring Boot Security

## 1. Security Configuration
- **FilterChain Configuration**: Define explicit authorization matching.
  ```java
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth
              .requestMatchers("/api/public/**").permitAll()
              .anyRequest().authenticated()
          );
      return http.build();
  }
  ```

## 2. Secure Headers & CORS
- Enforce secure headers and proper CORS mapping:
  ```java
  http.headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()));
  ```

## 3. Verification Checklist
- [ ] CSRF configurations secured.
- [ ] CORS policies explicitly defined (no wide wildcards `*` in production).
- [ ] Session management configured as stateless for REST APIs.
