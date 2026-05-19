---
name: ts-architecture
description: TypeScript directory layout, type safety, naming, and dependency patterns.
---

# TypeScript Architecture

## 1. Type Safety
- **Strict Mode**: Enforce `"strict": true` in `tsconfig.json`.
- **No any**: Avoid `any`; use `unknown` or explicit types.

## 2. Modules & Layout
- Enforce absolute import paths.
- Structure folders by feature domain.

## 3. Verification Checklist
- [ ] No `any` type used without justification.
- [ ] TS strict mode enabled.
