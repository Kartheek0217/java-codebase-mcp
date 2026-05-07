# Sprint 1: Core Infrastructure [CLOSED]

## Overview
Initialized the main Spring Boot project with core infrastructure.

### Technical Stack
- **Spring Boot**: 4.0.6
- **JDK**: 25
- **Database**: H2 (In-memory/File)
- **Migrations**: Spring SQL Init (`schema.sql`)
- **Threading**: Virtual Threads (Loom)
- **Observability**: Spring Boot Actuator

### Components Implemented
- `VirtualThreadConfig`: Custom task executor for virtual threads.
- `StatusController`: `/status` endpoint for basic metadata.
- `HealthController`: `/health` endpoint for UP/DOWN status.
- `schema.sql`: Initial `symbols` table definition.

### Verification
- `mvn clean package` succeeded.
- Application context loads with H2 and SQL init.
- Virtual thread executor bean registered.

## Setup Guide
1.  Ensure JDK 25 is installed.
2.  Run `mvn spring-boot:run`.
3.  Test endpoints:
    - `curl http://localhost:8080/status`
    - `curl http://localhost:8080/health`

---
**Next Steps:** See [Project Plan](PROJECT_PLAN.md) for Sprint 2 transition.
