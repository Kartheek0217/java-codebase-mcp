# 🚀 Java Codebase MCP Middleware

A high-fidelity, high-performance Java-based middleware designed to index local code repositories and provide structured metadata, AST-extracted symbols, and semantic search capabilities for AI agents through the Model Context Protocol (MCP).

## 🛠 Technology Stack

- **Core:** Spring Boot 4.0.6, **JDK 25** (Utilizing Loom Virtual Threads for extreme-concurrency I/O)
- **Indexing:** **Apache Lucene 10.1.0** (High-performance full-text search with Near-Real-Time updates)
- **Parsing:** **JavaParser 3.26.2** (Deep AST symbol extraction and call hierarchy analysis)
- **Web Automation:** **Microsoft Playwright 1.59.0** (Headless browser orchestration and data extraction)
- **Web Intelligence:** **Jsoup 1.18.1** (HTML parsing and structural analysis)
- **Persistence:** **H2 Database** (Efficient local metadata storage with JDBC batching)
- **Git Integration:** **JGit 6.8.0** (Native repository status and operations)
- **API Spec:** **SpringDoc OpenAPI 2.8.5** (Swagger UI & Interactive Documentation)
- **Caching:** **Caffeine Cache** (Optimized retrieval for frequent queries and topology maps)

## 📂 Project Structure

```text
src/main/java/com/mcp/
├── Application.java        # Spring Boot Entry Point
├── analysis/               # Lucene Analyzers (Code-aware tokenization)
├── config/                 # Configurations (Virtual Threads, Lucene, Browser, Exception Handling)
├── controller/             # REST API Surface (System, Codebase, Project, Browser, Mcp)
├── dto/                    # Data Transfer Objects (API Contracts & Models)
├── entity/                 # Persistence layer (Project, FileMetadata, Symbol, Task, Rule, Skill)
├── model/                  # Domain business models (Status, Priority, Enums)
├── properties/             # Configuration Properties (Type-safe property mapping)
├── repository/             # Spring Data JPA repositories
├── service/                # Core logic (Indexing, Git, Playwright, Tasks, AI Skills, Topology)
├── util/                   # Common utilities (CodeUtils, LLM Response Optimizer)
└── web/                    # Web-related components and filtering
```

## ✨ Key Features

- **🚀 High-Performance Persistence:** Optimized for high-throughput metadata storage with JDBC batching and sequence-based ID generation.
- **⚡ Loom-Powered Concurrency:** Utilizing Java 25 Virtual Threads to handle thousands of parallel file indexing tasks without the overhead of traditional thread pools.
- **🧠 Intelligent AST Extraction:** Precise extraction of Classes, Methods, Fields, and Interfaces with full Call Hierarchy tracking via JavaParser.
- **🔍 Near-Real-Time Content Search:** Lucene-powered full-text search with an optimized `SearcherManager` for immediate searchability of changes.
- **🔄 Bulk Indexing Mode:** High-speed initial project scanning with deferred commits and optimized RAM buffers.
- **🌐 Advanced Browser Orchestration:** Managed Playwright sessions with automatic idle cleanup and resource limit enforcement.
- **🐙 Integrated Git Workflows:** Inspect repository status, stage files, and commit changes directly through the MCP interface.
- **📋 Task & Rule Engine:** Maintain project-specific implementation plans, nested task steps, and AI agent rules.
- **📉 LLM Context Optimization:** Built-in compression and filtering of codebase context to maximize token efficiency for LLMs.

## 🚀 Performance & Tuning

The system is engineered for low-latency and high-throughput operation:

- **JDBC Batching**: Leverages `SEQUENCE` based ID generation to enable Hibernate batch inserts, significantly reducing round-trips to the H2 database during heavy indexing.
- **JVM Optimizations:** Pre-configured for low-latency GC, string deduplication, and optimized for Virtual Thread workloads.
- **Zero-Copy Checksumming**: Uses `DigestInputStream` to hash files in a single pass while reading.
- **Incremental Indexing**: Only changed files are re-indexed based on SHA-256 checksums.
- **Smart Exclusions**: Automatically skips `node_modules`, `target`, `build`, `.git`, and other common generated folders.

### ⚙️ Performance Tuning (application.properties)

| Property | Default | Description |
|----------|---------|-------------|
| `spring.jpa.properties.hibernate.jdbc.batch_size` | `50` | Batch size for SQL inserts/updates |
| `mcp.indexing.concurrency` | `20` | Max concurrent files to index |
| `lucene.ram.buffer-size` | `128.0` | RAM buffer size (MB) for indexing |
| `mcp.browser.max-sessions` | `10` | Maximum concurrent browser sessions |

## 📡 API Endpoints (Summary)

| Category | Description |
|----------|-------------|
| **System** | Health, Info, System Statistics |
| **Projects** | Registration, Scanning, Topology, Stats |
| **Codebase** | Search, Symbols, Summarization, Endpoint Analysis |
| **Browser** | Navigation, Content Extraction, Screenshots, Interactions |
| **Agent** | Task Management, Step Updates, Skill Learning, Rules |

## 💻 Getting Started

### Prerequisites
- **JDK 25** (Required for Virtual Threads)
- **Maven 4.0+**

### Installation & Run
1. **Build:** `mvn clean package`
2. **Run:** `mvn spring-boot:run`
3. **Docs:** Visit `http://localhost:9696/swagger-ui.html`

---
*Developed for the Advanced Agentic Coding ecosystem.*
