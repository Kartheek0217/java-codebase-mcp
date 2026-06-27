---
name: spring-boot-utilities
description: Jackson JSON configuration, logging, caching, and validation helpers.
---

# Spring Boot Utilities

## 1. Jackson Configuration
- **Date/Time Handling**: Register `JavaTimeModule` to support modern Java 8 time objects.
  ```java
  @Bean
  public ObjectMapper objectMapper() {
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new JavaTimeModule());
      mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      return mapper;
  }
  ```

## 2. Validation
- Enforce validation rules on controller requests using `@Valid` and standard constraints.
  ```java
  public class TaskRequest {
      @NotBlank @Size(max = 100) private String title;
  }
  ```

## 3. Verification Checklist
- [ ] Time zones and dates serialized using standard ISO formats.
- [ ] API payloads validated at controller endpoints.
