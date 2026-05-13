# 🚀 Java Codebase MCP Middleware

A high-performance Java-based middleware designed to index local code repositories and provide structured metadata, AST-extracted symbols, and semantic search capabilities for AI agents through the Model Context Protocol (MCP).

## 🛠 Technology Stack

- **Core:** Spring Boot 4.0.6, **JDK 25** (Utilizing Loom Virtual Threads for high-concurrency I/O)
- **Indexing:** **Apache Lucene 10.1.0** (High-performance full-text and semantic search)
- **Parsing:** **JavaParser 3.26.2** (Deep AST symbol extraction and call hierarchy analysis)
- **Web Automation:** **Microsoft Playwright** (Headless browser orchestration and data extraction)
- **Web Intelligence:** **Jsoup** (HTML parsing and DuckDuckGo search integration)
- **Persistence:** **H2 Database** with Vector Similarity support (Efficient local metadata and embedding storage)
- **Git Integration:** **JGit 6.8.0** (Native repository status and operations)
- **Live Sync:** **Directory Watcher 0.18.0** (Real-time filesystem event tracking)
- **API Spec:** **SpringDoc OpenAPI 2.8.5** (Swagger UI & Documentation)
- **Caching:** **Caffeine Cache** (Optimized retrieval for frequent queries)

## 📂 Project Structure

```text
src/main/java/com/mcp/
├── Application.java        # Main Entry Point
├── analysis/               # Lucene Analyzers (Code-aware tokenization & analysis)
├── config/                 # Configurations (Browser, Lucene, Virtual Threads, OpenAPI)
├── controller/             # REST API Surface (Agent, Browser, Codebase, Git, Web, Vector)
├── dto/                    # Data Transfer Objects (Contracts for API & Browser)
├── entity/                 # Persistence layer (Project, FileMetadata, Symbol, SymbolVector, BrowserSession)
├── model/                  # Domain business models (Statuses, Enums, FileEvents)
├── repository/             # Spring Data JPA repositories (JPA + Native Vector Queries)
├── service/                # Core logic (Indexing, Git, Playwright, Task Management, Semantic Search)
├── util/                   # Common utilities (Compression, URL validation, Code utils)
└── web/                    # Web-specific components (Search Providers like DuckDuckGo)
```

## ✨ Key Features

- **⚡ Virtual Thread Powered:** Utilizing Java 21+ Virtual Threads (Project Loom) for blazing-fast parallel scanning, indexing, and web requests.
- **🧠 Deep AST Analysis:** Precise symbol extraction with JavaParser, tracking Classes, Methods, Fields, and complex Call Hierarchies.
- **🔍 Semantic & Vector Search:** 
    - Lucene-powered full-text search with custom `CodeAnalyzer`.
    - **H2 Vector Similarity**: Native vector search for finding nearest code symbols based on embeddings.
- **🌐 Browser Orchestration:** Managed Playwright sessions for navigating, screenshotting, and interacting with web pages in real-time.
- **📡 Quick Web Extraction:** On-demand data extraction from any URL using headless browser sessions.
- **🔍 Native Web Search:** Built-in DuckDuckGo integration for real-time web research without requiring external API keys.
- **🔄 Real-time Synchronization:** Active directory watching keeps the index in sync with filesystem changes instantly.
- **🐙 Integrated Git Operations:** Full support for staging, committing, and inspecting repository state via JGit.
- **📋 AI Agent Engine:** Maintain project-specific rules, manage agent skills, and track implementation tasks.

## ⚙️ Configuration Specifications

Customizable via `application.properties` or environment variables:

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.browser.headless` | `true` | Run Playwright in headless mode |
| `mcp.browser.browser-type` | `chromium` | Browser engine (`chromium`, `firefox`, `webkit`) |
| `mcp.browser.max-sessions` | `10` | Maximum concurrent browser sessions |
| `lucene.ram.buffer-size` | `64.0` | RAM buffer size in MB for Lucene indexing |
| `lucene.index.path` | `index` | Path to store Lucene indexes |

## 📡 API Documentation

### 📁 Projects & Indexing
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/projects` | List all registered projects |
| POST | `/api/projects` | Register a project and start indexing |
| DELETE | `/api/projects/{id}` | Remove a project and its index |
| GET | `/api/projects/{id}/stats` | Get indexing statistics and symbol counts |
| GET | `/api/projects/{id}/topology` | Retrieve project structure and dependencies |

### 🔍 Code & Vector Search
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/index/{id}/search-content` | Full-text content search via Lucene |
| GET | `/api/ai/symbols` | Global symbol search by name |
| POST | `/api/vectors/seed` | Seed symbols with vectors for testing |
| GET | `/api/vectors/search` | Perform k-NN similarity search for symbols |

### 🌐 Browser & Web
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/browser/sessions` | Create a new Playwright session |
| POST | `/api/browser/{id}/navigate` | Navigate to a specific URL |
| GET | `/api/browser/{id}/content` | Get current page HTML and metadata |
| GET | `/api/browser/{id}/screenshot` | Capture current page screenshot |
| GET | `/api/web/search` | Perform web search via DuckDuckGo |
| GET | `/api/web/extract` | Extract structured data from a URL |

### 🤖 Agent & Git
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/session/start` | Initialize stateful AI session |
| GET | `/api/git-info/projects/{id}/status` | Get detailed Git status |
| POST | `/api/git-info/projects/{id}/commit` | Create a new Git commit |
| GET | `/api/ai/tasks` | List all tasks for a project |
| POST | `/api/ai/tasks` | Create a new implementation task |

## 💻 Getting Started

### Prerequisites
- **JDK 25** (Required for Virtual Threads and latest language features)
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
   Navigate to `http://localhost:9696/swagger-ui.html` for full interactive documentation.

---
*Note: This middleware is optimized for local development environments, providing high-fidelity context for LLM-driven coding workflows.*
