# 🚀 Java Codebase MCP Middleware

A high-performance Java-based middleware designed to index local code repositories and provide structured metadata, AST-extracted symbols, and semantic search capabilities for AI agents through the Model Context Protocol (MCP).

## 🛠 Technology Stack

- **Core:** Spring Boot 4.0.6, JDK 25 (Loom Virtual Threads)
- **Indexing:** Apache Lucene 10.1.0 (High-performance full-text search)
- **Parsing:** JavaParser 3.26.2 (Deep AST symbol extraction)
- **Persistence:** H2 Database (Efficient local metadata storage)
- **Git Integration:** JGit 6.8.0 (Repository status and operations)
- **Live Sync:** Directory Watcher 0.18.0 (Real-time filesystem event tracking)
- **API Spec:** SpringDoc OpenAPI 2.8.5 (Swagger UI & Documentation)
- **Caching:** Caffeine Cache (Optimized retrieval for frequent queries)

## 📂 Project Structure

```text
src/main/java/com/mcp/
├── Application.java        # Main Entry Point
├── config/                 # Configurations (Lucene, JPA, Virtual Threads, OpenAPI)
├── controller/             # REST API Surface (Agent, Indexing, Projects, Git)
├── dto/                    # Data Transfer Objects for API contracts
├── entity/                 # Persistence layer (Project, FileMetadata, Symbol)
├── health/                 # Placeholder for future health indicators
├── model/                  # Domain business models
├── repository/             # Spring Data JPA repositories
├── scheduler/              # Placeholder for future scheduled tasks
└── service/                # Core logic (Indexing, Scanning, Git, Watcher)
```

## ✨ Key Features

- **⚡ Blazing Fast Scanning:** Parallel initial scan utilizing Java Virtual Threads for maximum I/O throughput.
- **🧠 Deep AST Analysis:** Symbol-aware indexing with JavaParser for precise code understanding (Classes, Methods, Fields).
- **🔍 Semantic Search:** Lucene-powered full-text and symbol search with contextual awareness.
- **🔄 Real-time Synchronization:** Active directory watching to keep the index in sync with filesystem changes instantly.
- **🤖 AI Agent Optimized:** Dedicated endpoints designed for LLMs to explore architecture and gather relevant code context.
- **🐙 Native Git Integration:** Full support for staging, committing, and inspecting repository state directly via API.

## 📡 API Documentation

The server exposes a comprehensive REST API. Explore the interactive documentation via Swagger UI at `/swagger-ui.html`.

### 📁 Projects Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/projects` | List all registered projects |
| POST | `/api/projects` | Register a new project and start indexing |
| GET | `/api/projects/{id}` | Get details of a specific project |
| DELETE | `/api/projects/{id}` | Remove a project and its associated indices |

### 🔍 Indexing & Search
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/index/{projectId}/status` | Get indexing statistics (file & symbol counts) |
| POST | `/api/index/{projectId}/trigger-scan` | Manually force a directory re-scan |
| POST | `/api/index/{projectId}/reconcile` | Perform deep sync between DB and filesystem |
| GET | `/api/index/{projectId}/search-content` | Full-text content search via Lucene |
| GET | `/api/index/{projectId}/files/search` | Substring search for file paths |

### 🤖 AI Agent Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/session/start` | Initialize a new stateful AI session |
| GET | `/api/ai/session/{id}` | Retrieve history and state of a specific session |
| GET | `/api/ai/context` | Get full file content with attached AST symbols |
| GET | `/api/ai/symbols` | Global symbol search (CLASS, METHOD, FIELD) |
| GET | `/api/ai/suggest` | Semantic snippet suggestions based on query |
| GET | `/api/ai/history` | View recent file/query access history |

### 🐙 Git Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/git-info` | Get global Git metadata for the server itself |
| GET | `/api/git-info/projects/{id}/status` | Get detailed Git status for a project |
| POST | `/api/git-info/projects/{id}/stage` | Stage files or patterns (git add) |
| POST | `/api/git-info/projects/{id}/commit` | Create a new commit with staged changes |
| POST | `/api/git-info/projects/{id}/discard` | Revert changes in specific files |

### ⚙️ System Status
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Check server, database, and index health |
| GET | `/status` | Get application version, uptime, and project counts |

## 💻 Getting Started

### Prerequisites
- **JDK 25** (Required for Virtual Threads support)
- **Maven 3.9+**

### Quick Start
1. **Build the project:**
   ```bash
   mvn clean package
   ```
2. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```
3. **Explore the API:**
   Navigate to `http://localhost:8080/swagger-ui.html` to view the OpenAPI documentation.

---
*Note: This middleware is optimized for local development environments, focusing on high-fidelity context for LLM-driven coding tasks.*

