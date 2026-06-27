---
name: spring-boot-jpa
description: Optimizing Hibernate JPA fetch strategies, transaction scopes, and caching.
---

# Spring Boot JPA & Hibernate

## 1. Fetch Strategies
- **Lazy Loading**: Use `FetchType.LAZY` for `@OneToMany` and `@ManyToMany`.
- **N+1 Avoidance**: Use Entity Graphs or JOIN FETCH queries to fetch associations.
  ```java
  @Query("SELECT p FROM Project p LEFT JOIN FETCH p.gitBranches WHERE p.id = :id")
  Optional<Project> findByIdWithBranches(Long id);
  ```

## 2. Transaction Management
- **Transaction Scope**: Keep `@Transactional` narrow. Use read-only transactions where appropriate.
  ```java
  @Transactional(readOnly = true)
  public ProjectDto getProject(Long id) { ... }
  ```
- **Dirty Checking**: Hibernate monitors managed entities. For read-only paths, detach or query DTOs.

## 3. Verification Checklist
- [ ] `@OneToMany` collections configured as `LAZY`.
- [ ] High-traffic reads fetch DTOs or use `readOnly = true`.
- [ ] Fetch queries specify JOIN FETCH / Entity Graph for lazy relations.
