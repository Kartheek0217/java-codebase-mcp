# 🚀 Java Codebase MCP Middleware

A high-performance Java-based middleware designed to index local code repositories and provide structured metadata, AST-extracted symbols, and semantic search capabilities for AI agents through the Model Context Protocol (MCP).

## 🛠 Technology Stack

- **Core:** Spring Boot 4.0.6, JDK 25 (Loom Virtual Threads for high-concurrency I/O)
- **Indexing:** Apache Lucene 10.1.0 (High-performance full-text and semantic search)
- **Parsing:** JavaParser 3.26.2 (Deep AST symbol extraction and call hierarchy analysis)
- **Web Automation:** Microsoft Playwright (Headless browser orchestration)
- **Web Intelligence:** Jsoup (HTML parsing and DuckDuckGo search integration)
- **Persistence:** H2 Database (Efficient local metadata storage)
- **Git Integration:** JGit 6.8.0 (Native repository status and operations)
- **Live Sync:** Directory Watcher 0.18.0 (Real-time filesystem event tracking)
- **API Spec:** SpringDoc OpenAPI 2.8.5 (Swagger UI & Documentation)
- **Caching:** Caffeine Cache (Optimized retrieval for frequent queries)

## 📂 Project Structure

```text
src/main/java/com/mcp/
├── Application.java        # Main Entry Point
├── analysis/               # Lucene Analyzers (Code-aware tokenization)
├── config/                 # Configurations (Browser, Lucene, Virtual Threads, OpenAPI)
├── controller/             # REST API Surface (Agent, Browser, Codebase, Git, Web)
├── dto/                    # Data Transfer Objects (Contracts for API & Browser)
├── entity/                 # Persistence layer (Project, FileMetadata, Symbol, BrowserSession)
├── model/                  # Domain business models (Statuses, Enums)
├── repository/             # Spring Data JPA repositories
├── service/                # Core logic (Indexing, Git, Playwright, Task Management)
├── util/                   # Common utilities (Compression, URL validation)
└── web/                    # Web-specific components (Crawler, Search Providers)
```

## ✨ Key Features

- **⚡ Virtual Thread Powered:** Utilizing Java 21+ Virtual Threads for blazing-fast parallel scanning and web requests.
- **🧠 Deep AST Analysis:** Precise symbol extraction with JavaParser, tracking Classes, Methods, Fields, and Call Hierarchies.
- **🔍 Semantic Search:** Lucene-powered indexing with custom `CodeAnalyzer` for meaningful code search.
- **🌐 Browser Orchestration:** Managed Playwright sessions for navigating, screenshotting, and extracting data from web pages.
- **🔍 Web Search:** Built-in DuckDuckGo integration for real-time web research without API keys.
- **🔄 Real-time Synchronization:** Active directory watching keeps the index in sync with filesystem changes instantly.
- **🐙 Native Git Integration:** Full support for staging, committing, and inspecting repository state.
- **📋 Task & Rule Engine:** Maintain project-specific rules and track implementation tasks for AI agents.

## ⚙️ Configuration Specifications

The application can be customized via `application.properties` or environment variables:

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.browser.headless` | `true` | Run Playwright in headless mode |
| `mcp.browser.browser-type` | `chromium` | Browser engine (`chromium`, `firefox`, `webkit`) |
| `mcp.browser.max-sessions` | `10` | Maximum concurrent browser sessions |
| `mcp.web.crawler.max-pages` | `10` | Default limit for web crawl jobs |
| `mcp.web.crawler.delay-ms` | `1000` | Polite delay between crawl requests |
| `lucene.ram.buffer-size` | `64.0` | RAM buffer size in MB for Lucene indexing |

## 📡 API Documentation

### 📁 Projects & Indexing
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/projects` | List all registered projects |
| POST | `/api/projects` | Register a project and start indexing |
| GET | `/api/index/{id}/status` | Get indexing statistics |
| POST | `/api/index/{id}/search-content` | Full-text content search |

### 🌐 Browser & Web
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/browser/sessions` | Create a new Playwright session |
| POST | `/api/browser/{id}/navigate` | Navigate to a specific URL |
| GET | `/api/browser/{id}/screenshot` | Capture current page screenshot |
| GET | `/api/web/search` | Perform web search via DuckDuckGo |
| POST | `/api/web/crawl` | Start a background web crawl job |
| GET | `/api/web/crawl/search` | Search through locally crawled web content |

### 🤖 Agent & Git
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ai/session/start` | Initialize stateful AI session |
| GET | `/api/ai/symbols` | Global symbol search |
| GET | `/api/git-info/projects/{id}/status` | Get detailed Git status |
| POST | `/api/git-info/projects/{id}/commit` | Create a new Git commit |

## 💻 Getting Started

### Prerequisites
- **JDK 25** (Required for Loom Virtual Threads support)
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
   Navigate to `http://localhost:8080/swagger-ui.html` for full documentation.

---
*Note: This middleware is optimized for local development environments, focusing on providing high-fidelity context for LLM-driven coding tasks.*
