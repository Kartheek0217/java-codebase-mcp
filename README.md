# 🚀 Java Codebase MCP Middleware

A high-performance Java-based middleware designed to index local code repositories and provide structured metadata, AST-extracted symbols, and semantic search capabilities for AI agents through the Model Context Protocol (MCP).

## 🛠 Technology Stack

- **Core:** Spring Boot 4.0.6, JDK 25
- **Indexing:** Apache Lucene 10.1.0 (Full-text search)
- **Parsing:** JavaParser 3.26.2 (AST-based symbol extraction)
- **Persistence:** H2 Database (Local storage)
- **Git Integration:** JGit 6.8.0
- **Concurrency:** Java Virtual Threads (Project Loom) for high-throughput I/O
- **Caching:** Caffeine Cache

## 📂 Project Structure

```text
src/main/java/com/mcp/
├── Application.java        # Main Entry Point
├── config/                 # Spring Boot Configurations (Lucene, JPA, etc.)
├── controller/             # REST Controllers (Agent, Indexing, Projects, Git)
├── dto/                    # Data Transfer Objects
├── entity/                 # JPA Entities (Project, FileMetadata, Symbol)
├── health/                 # Custom Health Indicators
├── model/                  # Domain Models
├── repository/             # Spring Data Repositories
├── scheduler/              # Background Tasks (Index reconciliation)
└── service/                # Core Business Logic (LuceneIndexService, SymbolService, etc.)
```

## ✨ Key Features

- **Blazing Fast Scanning:** Parallel initial scan using Java Virtual Threads.
- **Intelligent Indexing:** Symbol-aware indexing with AST parsing for deep code understanding.
- **Semantic Search:** Lucene-powered full-text and symbol search across the codebase.
- **AI Agent Optimized:** Specialized endpoints for AI agents to gather context and navigate files.
- **Git Integration:** Built-in support for staging, committing, and checking repository status.
- **Real-time Synchronization:** Reconciles the database with the filesystem to ensure index freshness.

## 📡 API Documentation

The server exposes a comprehensive REST API. Below are the primary endpoint categories:

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
| POST | `/api/index/{projectId}/trigger-scan` | Manually trigger a directory scan |
| POST | `/api/index/{projectId}/reconcile` | Deep sync database with filesystem |
| GET | `/api/index/{projectId}/status` | Get indexing statistics (files, symbols) |
| GET | `/api/index/{projectId}/search-content` | Full-text search via Lucene |
| GET | `/api/index/{projectId}/files/search` | Substring search for file paths |

### 🤖 AI Agent Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/session/start` | Initialize a new AI session |
| GET | `/api/ai/symbols` | Search for symbols (CLASS, METHOD, FIELD) |
| GET | `/api/ai/suggest` | Suggest code snippets based on query |
| GET | `/api/ai/context` | Get full file content with AST symbols |
| GET | `/api/ai/history` | Retrieve recent file/query access history |

### 🐙 Git Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/git-info/projects/{projectId}/status` | Get detailed Git status |
| POST | `/api/git-info/projects/{projectId}/stage` | Stage files (git add) |
| POST | `/api/git-info/projects/{projectId}/commit` | Commit staged changes |
| POST | `/api/git-info/projects/{projectId}/discard` | Revert local changes |

### ⚙️ System Status
- **Health Check:** `GET /health`
- **System Metadata:** `GET /status`

## 💻 Getting Started

### Prerequisites
- **JDK 25** (Required for Virtual Threads)
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
3. **Access OpenAPI UI:**
   Open `http://localhost:8080/swagger-ui.html` to explore and test the API interactively.

---
*Note: This middleware is optimized for local development environments and focuses on providing high-fidelity code context to LLMs.*

