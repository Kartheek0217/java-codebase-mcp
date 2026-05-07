# MCP Repo Indexing Middleware

A high-performance Java-based middleware designed to index local code repositories and provide structured metadata and search capabilities for AI agents.

## 🚀 Overview

This project provides a robust indexing layer that scans local filesystems, extracts symbols using AST parsing, and exposes a REST API. It is optimized for local usage, leveraging modern Java features like Virtual Threads (Project Loom) for high-throughput I/O.

## 🛠 Technology Stack

- **Core (planned):** Spring Boot 4.0, JDK 25
- **Concurrency (planned):** Java Virtual Threads (Project Loom)
- **Indexing (planned):** Apache Lucene (High-performance full-text search)
- **Database (planned):** H2 (Local persistence via Liquibase)
- **Build System:** Maven
- **File Watching (planned):** JNotify / WatchService (Real-time event tracking)

## ✨ Key Features

- **Blazing Fast Scanning:** Parallel initial scan using Virtual Threads.
- **Intelligent Indexing:** Symbol-aware ranking (Definitions > Usages).
- **AST Parsing:** Multi-language symbol extraction via Tree-sitter.
- **Real-time Updates:** File system watcher for instant index reconciliation.
- **Agent-Ready API:** REST endpoints for search, snippet extraction, and structure browsing.
- **Optimized Caching:** Byte-aware Caffeine caching for file contents.

## 💻 Getting Started (Local Usage)

### Prerequisites
- JDK 25
- Maven 3.9+

### Quick Start
1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   cd java-codebase-mcp
   ```

2. **Build the project:**
   ```bash
   mvn clean package
   ```

3. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

4. **Health and metrics:**
   - `http://localhost:8080/status`
   - `http://localhost:8080/actuator/health`
   - `http://localhost:8080/actuator/prometheus`

5. **Sprint 0 runners:**
   See [docs/SPRINT0.md](docs/SPRINT0.md).

## 📅 Roadmap & Development

For detailed sprint tasks, technical roadmaps, and the risk matrix, please refer to the [PROJECT_PLAN.md](PROJECT_PLAN.md).

---
*Note: This project is intended for local usage only. Standard Maven repository libraries are used to ensure portability across local environments.*
