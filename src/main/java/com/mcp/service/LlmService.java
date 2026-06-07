package com.mcp.service;

import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.LlmActionRequest;
import com.mcp.entity.Symbol;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * High-level LLM service that composes {@link LlmClient} with codebase indexes
 * and Playwright browser integration.
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    private static final int MAX_CONTEXT_CHARS = 6_000;

    private final LlmClient llmClient;
    private final SymbolRepository symbolRepository;
    private final ProjectRepository projectRepository;
    private final CodeSummarizerService codeSummarizerService;
    private final LuceneIndexService luceneIndexService;
    private final HeadlessBrowserService headlessBrowserService;
    private final BrowserSessionManager browserSessionManager;

    @Value("classpath:skills/global/jcb/SKILL.md")
    private Resource jcbSkillResource;

    private String jcbSystemPrompt = "";

    public LlmService(LlmClient llmClient,
            SymbolRepository symbolRepository,
            ProjectRepository projectRepository,
            CodeSummarizerService codeSummarizerService,
            LuceneIndexService luceneIndexService,
            HeadlessBrowserService headlessBrowserService,
            BrowserSessionManager browserSessionManager) {
        this.llmClient = llmClient;
        this.symbolRepository = symbolRepository;
        this.projectRepository = projectRepository;
        this.codeSummarizerService = codeSummarizerService;
        this.luceneIndexService = luceneIndexService;
        this.headlessBrowserService = headlessBrowserService;
        this.browserSessionManager = browserSessionManager;
    }

    @PostConstruct
    public void init() {
        try (InputStream is = jcbSkillResource.getInputStream()) {
            jcbSystemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Failed to load global JCB skill from classpath: {}. Trying fallback path.", e.getMessage());
            try {
                Path path = Paths.get("src/main/resources/skills/global/jcb/SKILL.md");
                if (Files.exists(path)) {
                    jcbSystemPrompt = Files.readString(path);
                }
            } catch (Exception ex) {
                logger.error("Failed to read fallback JCB skill file: {}", ex.getMessage());
            }
        }
    }

    private List<String> detectProjectTypes(String rootPath) {
        List<String> types = new java.util.ArrayList<>();
        if (rootPath == null || rootPath.isBlank()) {
            return types;
        }
        Path root = Paths.get(rootPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return types;
        }

        Path pomXml = root.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            types.add("Maven");
            try {
                String content = Files.readString(pomXml);
                if (content.contains("spring-boot")) {
                    types.add("Spring Boot");
                }
            } catch (Exception ignored) {
            }
        }

        Path packageJson = root.resolve("package.json");
        if (Files.exists(packageJson)) {
            types.add("Node/JS");
            try {
                String content = Files.readString(packageJson);
                if (content.contains("\"vite\"")) {
                    types.add("Vite");
                }
                if (content.contains("\"vue\"")) {
                    types.add("Vue");
                }
                if (content.contains("\"react\"")) {
                    types.add("React");
                }
                if (content.contains("\"@angular/")) {
                    types.add("Angular");
                }
                if (content.contains("\"playwright\"")) {
                    types.add("Playwright E2E");
                }
            } catch (Exception ignored) {
            }
        }

        if (Files.exists(root.resolve("playwright.config.ts")) || Files.exists(root.resolve("playwright.config.js"))) {
            if (!types.contains("Playwright E2E")) {
                types.add("Playwright E2E");
            }
        }

        if (Files.exists(root.resolve("angular.json"))) {
            if (!types.contains("Angular")) {
                types.add("Angular");
            }
        }

        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            types.add("Gradle");
        }

        if (types.isEmpty()) {
            types.add("Plain HTML/CSS/JS");
        }

        return types;
    }

    private String getDynamicContextInstructions(List<String> types) {
        if (types == null || types.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== DYNAMIC PROJECT CONTEXT ===\n");
        sb.append("This codebase uses: ").append(String.join(", ", types)).append(".\n");
        sb.append("Tailor your responses specifically to these technologies and follow their standard conventions.\n");

        if (types.contains("Spring Boot")) {
            sb.append("- Follow enterprise Java Spring Boot conventions: dependency injection (constructor injection), REST controllers, JPA/Hibernate best practices, DTO mapping, and transactional safety.\n");
        }
        if (types.contains("Vue")) {
            sb.append("- Follow modern Vue (Vite-backed Vue 3 Composition API preferably) conventions: ref/reactive state management, component modularity, scoped styles, and clear lifecycles.\n");
        }
        if (types.contains("React")) {
            sb.append("- Follow modern React standards: functional components, hooks (useState, useEffect, useMemo, useCallback), immutability, and clean custom hooks.\n");
        }
        if (types.contains("Angular")) {
            sb.append("- Follow Angular design patterns: TypeScript-based components and services, DI, RxJS observables, reactive forms, and modular app layouts.\n");
        }
        if (types.contains("Playwright E2E")) {
            sb.append("- Follow Playwright test automation standards: Page Object Model (POM), async/await assertions, locators, and isolated fixtures.\n");
        }
        sb.append("=== END CONTEXT ===\n");
        return sb.toString();
    }

    private String getSystemPrompt(Long projectId, String baseSystemPrompt) {
        String projectSpecificInstructions = "";
        try {
            if (projectId != null) {
                com.mcp.entity.Project project = projectRepository.findById(projectId).orElse(null);
                if (project != null) {
                    List<String> types = detectProjectTypes(project.getRootPath());
                    projectSpecificInstructions = getDynamicContextInstructions(types);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not determine dynamic project types: {}", e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(baseSystemPrompt);
        if (!projectSpecificInstructions.isEmpty()) {
            sb.append("\n\n").append(projectSpecificInstructions);
        }
        if (jcbSystemPrompt != null && !jcbSystemPrompt.isBlank()) {
            sb.append("\n\n").append(jcbSystemPrompt);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Message Builders for Streaming
    // -------------------------------------------------------------------------

    public List<LlmClient.Message> buildExplainSymbolMessages(Long projectId, Long symbolId) {
        Symbol symbol = symbolRepository.findById(symbolId)
                .orElseThrow(() -> new IllegalArgumentException("Symbol not found: " + symbolId));

        String fileContent = readFileSafe(symbol.getFilePath());
        String structure = codeSummarizerService.extractStructure(fileContent);
        String truncated = truncate(structure, MAX_CONTEXT_CHARS);

        String userPrompt = """
                You are an expert developer. Explain the following code component clearly and concisely.
                Focus on: what it does, its responsibilities, and any important patterns it uses.

                Symbol: %s (%s)
                File: %s

                Source code (structural skeleton):
                ```java
                %s
                ```

                Provide a plain explanation suitable for a developer unfamiliar with this codebase.
                """.formatted(symbol.getName(), symbol.getType(), symbol.getFilePath(), truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a senior code analyst. Answer concisely and accurately.")),
                new LlmClient.Message("user", userPrompt));
    }

    public List<LlmClient.Message> buildExplainFileMessages(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String structure = codeSummarizerService.extractStructure(fileContent);
        String truncated = truncate(structure, MAX_CONTEXT_CHARS);

        String userPrompt = """
                You are an expert developer. Analyse the following source file and provide:
                1. A one-paragraph high-level summary of the file's purpose.
                2. A bullet list of the key classes, interfaces, or methods and what each does.
                3. Any notable design patterns or architectural concerns.

                File: %s

                ```java
                %s
                ```
                """.formatted(filePath, truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a senior code analyst. Answer concisely and accurately.")),
                new LlmClient.Message("user", userPrompt));
    }

    public List<LlmClient.Message> buildAskCodebaseMessages(Long projectId, String question) {
        List<ContentSearchResult> hits = luceneIndexService.searchContent(projectId, question, 5);
        String context = buildContextBlock(hits);

        String systemPrompt = """
                You are an expert developer assisting with codebase questions.
                You have been given relevant code snippets from the project's indexed codebase as context.
                Use the context to answer the user's question accurately. If the context doesn't contain
                enough information, say so clearly. Do not invent implementation details.
                === CODEBASE CONTEXT ===
                %s
                === END CONTEXT ===
                """.formatted(context.isEmpty() ? "(no relevant snippets found)" : context);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, systemPrompt)),
                new LlmClient.Message("user", question));
    }

    public List<LlmClient.Message> buildCodeReviewMessages(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String truncated = truncate(fileContent, MAX_CONTEXT_CHARS);

        String userPrompt = """
                Perform a professional code review on the following source file.
                Identify potential bugs, performance bottlenecks, security issues, and violations of clean code principles.
                Suggest actionable improvements.
                File: %s
                ```java
                %s
                ```
                """.formatted(filePath, truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a senior software engineer conducting a detailed code review.")),
                new LlmClient.Message("user", userPrompt));
    }

    public List<LlmClient.Message> buildCodeRefactorMessages(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String truncated = truncate(fileContent, MAX_CONTEXT_CHARS);

        String userPrompt = """
                Refactor and optimize the following code for better readability, efficiency, and performance.
                Provide the refactored code blocks and explain your optimization decisions.
                File: %s
                ```java
                %s
                ```
                """.formatted(filePath, truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a software architect specialized in code optimization.")),
                new LlmClient.Message("user", userPrompt));
    }

    public List<LlmClient.Message> buildCodeCommitMessages(Long projectId, String diff) {
        String truncated = truncate(diff, MAX_CONTEXT_CHARS);
        String userPrompt = """
                Generate a concise, descriptive Git commit message following Conventional Commits guidelines based on the following code diff:
                ```diff
                %s
                ```
                """.formatted(truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a Git assistant. Generate clean, descriptive, and conventional commit messages.")),
                new LlmClient.Message("user", userPrompt));
    }

    public List<LlmClient.Message> buildJavaDocMessages(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String truncated = truncate(fileContent, MAX_CONTEXT_CHARS);

        String userPrompt = """
                Generate clean, standard Javadoc documentation for all classes, fields, and methods in the following Java source file.
                Requirements:
                1. Include @author karthik.j in the class-level Javadoc.
                2. Output ONLY the complete modified Java source code inside a single markdown code block (```java ... ```) without any other explanation, text, or warnings.
                
                File: %s
                ```java
                %s
                ```
                """.formatted(filePath, truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a senior Java architect. Document code following strict Javadoc specifications.")),
                new LlmClient.Message("user", userPrompt));
    }

    public List<LlmClient.Message> buildJunitTestCasesMessages(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String truncated = truncate(fileContent, MAX_CONTEXT_CHARS);

        String userPrompt = """
                Generate a comprehensive suite of JUnit test cases (preferably JUnit 5) for the following Java source file.
                Include assertions for all main logical paths and boundary conditions.
                File: %s
                ```java
                %s
                ```
                """.formatted(filePath, truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are a senior QA engineer. Generate clean, complete JUnit test suites.")),
                new LlmClient.Message("user", userPrompt));
    }

    // -------------------------------------------------------------------------
    // Legacy Blocking API Surface (Redirects to builders)
    // -------------------------------------------------------------------------

    public String explainSymbol(Long projectId, Long symbolId) {
        return llmClient.chat(buildExplainSymbolMessages(projectId, symbolId), "code-analyse");
    }

    public String explainFile(Long projectId, String filePath) throws IOException {
        return llmClient.chat(buildExplainFileMessages(projectId, filePath), "code-analyse");
    }

    public String askCodebase(Long projectId, String question) {
        return llmClient.chat(buildAskCodebaseMessages(projectId, question), "code-analyse");
    }

    public String codeReview(Long projectId, String filePath) throws IOException {
        return llmClient.chat(buildCodeReviewMessages(projectId, filePath), "code-review");
    }

    public String codeRefactor(Long projectId, String filePath) throws IOException {
        return llmClient.chat(buildCodeRefactorMessages(projectId, filePath), "code-refactor");
    }

    public String codeCommit(Long projectId, String diff) {
        return llmClient.chat(buildCodeCommitMessages(projectId, diff), "code-commit");
    }

    public String javaDoc(Long projectId, String filePath) throws IOException {
        return llmClient.chat(buildJavaDocMessages(projectId, filePath), "java-doc");
    }

    public String junitTestCases(Long projectId, String filePath) throws IOException {
        return llmClient.chat(buildJunitTestCasesMessages(projectId, filePath), "junit-test-cases");
    }

    public String webSearchAndAnalyse(Long projectId, String query, String url) {
        String sessionId = null;
        try {
            sessionId = browserSessionManager.createSession();
            String pageText = fetchWebSearchContent(query, url, sessionId);
            List<LlmClient.Message> messages = buildWebSearchMessages(projectId, query, url, pageText);
            return llmClient.chat(messages, "web-search");
        } finally {
            if (sessionId != null) {
                browserSessionManager.closeSession(sessionId);
            }
        }
    }

    /**
     * Executes the LLM action and returns an SseEmitter that streams the response chunks.
     *
     * @param projectId unique project identifier
     * @param action    LLM action to execute
     * @param req       LlmActionRequest payload containing options
     * @return SseEmitter instance for SSE streaming
     */
    public SseEmitter streamResponse(Long projectId, String action, LlmActionRequest req) {
        SseEmitter emitter = new SseEmitter(180000L); // 3-minute timeout

        Thread.startVirtualThread(() -> {
            try {
                streamLlmAction(projectId, action, req, req.symbolId(), req.filePath(), req.query(), req.url(), req.diff(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE emitter send failed", e);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    // -------------------------------------------------------------------------
    // Streaming Core Method
    // -------------------------------------------------------------------------

    public void streamLlmAction(Long projectId, String action, LlmActionRequest req,
                                 Long symbolId, String filePath, String query, String url, String diff,
                                 java.util.function.Consumer<String> chunkConsumer) throws IOException {
        String taskType = switch (action.toLowerCase()) {
            case "explain-symbol" -> "code-analyse";
            case "explain-file" -> "code-analyse";
            case "ask" -> "code-analyse";
            case "code-review" -> "code-review";
            case "code-refactor", "code-optimise" -> "code-refactor";
            case "web-search" -> "web-search";
            case "code-commit" -> "code-commit";
            case "java-doc" -> "java-doc";
            case "junit-test-cases" -> "junit-test-cases";
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };

        if ("web-search".equalsIgnoreCase(action)) {
            String sessionId = null;
            try {
                sessionId = browserSessionManager.createSession();
                String q = query != null ? query : req.query();
                String u = url != null ? url : req.url();
                String pageText = fetchWebSearchContent(q, u, sessionId);
                List<LlmClient.Message> messages = buildWebSearchMessages(projectId, q, u, pageText);
                llmClient.streamChat(messages, taskType, chunkConsumer);
            } finally {
                if (sessionId != null) {
                    browserSessionManager.closeSession(sessionId);
                }
            }
            return;
        }

        List<LlmClient.Message> messages = switch (action.toLowerCase()) {
            case "explain-symbol" -> {
                Long sId = symbolId != null ? symbolId : req.symbolId();
                yield buildExplainSymbolMessages(projectId, sId);
            }
            case "explain-file" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield buildExplainFileMessages(projectId, path);
            }
            case "ask" -> buildAskCodebaseMessages(projectId, req.question());
            case "code-review" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield buildCodeReviewMessages(projectId, path);
            }
            case "code-refactor", "code-optimise" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield buildCodeRefactorMessages(projectId, path);
            }
            case "code-commit" -> {
                String d = diff != null ? diff : req.diff();
                yield buildCodeCommitMessages(projectId, d);
            }
            case "java-doc" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield buildJavaDocMessages(projectId, path);
            }
            case "junit-test-cases" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield buildJunitTestCasesMessages(projectId, path);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };

        llmClient.streamChat(messages, taskType, chunkConsumer);
    }

    // -------------------------------------------------------------------------
    // Web Search Helpers
    // -------------------------------------------------------------------------

    private String fetchWebSearchContent(String query, String url, String sessionId) {
        String pageText = "";
        if (url != null && !url.isBlank()) {
            logger.info("R&D Web Search - Navigating directly to URL: {}", url);
            headlessBrowserService.navigate(sessionId, url);
            String title = headlessBrowserService.getTitle(sessionId);
            String bodyText = headlessBrowserService.getContent(sessionId);
            pageText = "URL: " + url + "\nTitle: " + title + "\nContent:\n" + bodyText;
        } else if (query != null && !query.isBlank()) {
            String searchUrl = "https://html.duckduckgo.com/html/?q="
                    + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            logger.info("R&D Web Search - Searching DuckDuckGo for: {}", query);
            headlessBrowserService.navigate(sessionId, searchUrl);
            String bodyText = headlessBrowserService.getContent(sessionId);
            pageText = "Search Query: " + query + "\nResults Page Content:\n" + bodyText;
        } else {
            throw new IllegalArgumentException("Either 'url' or 'query' must be provided for web-search");
        }
        return pageText;
    }

    private List<LlmClient.Message> buildWebSearchMessages(Long projectId, String query, String url, String pageText) {
        String truncated = truncate(pageText, 8000);
        String userPrompt = """
                You are an expert research analyst. Review the following web page content/search results and provide a comprehensive R&D summary.
                Focus on answering the core query or detailing the technology, specifications, or findings.
                Context:
                %s
                """.formatted(truncated);

        return List.of(
                new LlmClient.Message("system", getSystemPrompt(projectId, "You are an expert research assistant. Synthesize web search results into a clean R&D report.")),
                new LlmClient.Message("user", userPrompt));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildContextBlock(List<ContentSearchResult> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentSearchResult hit : hits) {
            sb.append("File: ").append(hit.filePath()).append("\n");
            if (hit.matches() != null) {
                for (ContentSearchResult.ContentMatch match : hit.matches()) {
                    String lineContent = match.lineContent();
                    if (lineContent != null && !lineContent.isBlank()) {
                        sb.append("  [Line ~").append(match.lineNumber()).append("]: ")
                                .append(lineContent.strip()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }
        return truncate(sb.toString(), MAX_CONTEXT_CHARS);
    }

    private String readFileSafe(String absolutePath) {
        if (absolutePath == null) {
            return "";
        }
        try {
            return Files.readString(Paths.get(absolutePath));
        } catch (IOException e) {
            logger.warn("Could not read file for LLM context: {}", absolutePath);
            return "";
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null)
            return "";
        if (text.length() <= maxChars)
            return text;
        return text.substring(0, maxChars) + "\n... [truncated for token budget]";
    }
}
