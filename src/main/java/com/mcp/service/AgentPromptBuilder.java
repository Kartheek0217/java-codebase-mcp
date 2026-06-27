package com.mcp.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.SearchOptions;
import com.mcp.entity.Symbol;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;

@Service
public class AgentPromptBuilder {

    private static final Logger logger = LoggerFactory.getLogger(AgentPromptBuilder.class);
    private static final int MAX_CONTEXT_CHARS = 6_000;

    private final SymbolRepository symbolRepository;
    private final ProjectRepository projectRepository;
    private final CodeSummarizerService codeSummarizerService;
    private final LuceneIndexService luceneIndexService;

    @Value("classpath:skills/global/jcb/SKILL.md")
    private Resource jcbSkillResource;

    private String jcbSystemPrompt = "";

    public AgentPromptBuilder(SymbolRepository symbolRepository,
            ProjectRepository projectRepository,
            CodeSummarizerService codeSummarizerService,
            LuceneIndexService luceneIndexService) {
        this.symbolRepository = symbolRepository;
        this.projectRepository = projectRepository;
        this.codeSummarizerService = codeSummarizerService;
        this.luceneIndexService = luceneIndexService;
    }

    @PostConstruct
    public void init() {
        try (InputStream is = jcbSkillResource.getInputStream()) {
            jcbSystemPrompt = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Failed to load global JCB skill from classpath: {}", e.getMessage());
        }
    }

    private List<String> detectProjectTypes(String rootPath) {
        List<String> types = new ArrayList<>();
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
            sb.append(
                    "- Follow enterprise Java Spring Boot conventions: dependency injection (constructor injection), REST controllers, JPA/Hibernate best practices, DTO mapping, and transactional safety.\n");
        }
        if (types.contains("Vue")) {
            sb.append(
                    "- Follow modern Vue (Vite-backed Vue 3 Composition API preferably) conventions: ref/reactive state management, component modularity, scoped styles, and clear lifecycles.\n");
        }
        if (types.contains("React")) {
            sb.append(
                    "- Follow modern React standards: functional components, hooks (useState, useEffect, useMemo, useCallback), immutability, and clean custom hooks.\n");
        }
        if (types.contains("Angular")) {
            sb.append(
                    "- Follow Angular design patterns: TypeScript-based components and services, DI, RxJS observables, reactive forms, and modular app layouts.\n");
        }
        if (types.contains("Playwright E2E")) {
            sb.append(
                    "- Follow Playwright test automation standards: Page Object Model (POM), async/await assertions, locators, and isolated fixtures.\n");
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

    public List<AgentClient.Message> buildExplainSymbolMessages(Long projectId, Long symbolId) {
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
                new AgentClient.Message("system",
                        getSystemPrompt(projectId, "You are a senior code analyst. Answer concisely and accurately.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildExplainFileMessages(Long projectId, String filePath) throws IOException {
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
                new AgentClient.Message("system",
                        getSystemPrompt(projectId, "You are a senior code analyst. Answer concisely and accurately.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildAskCodebaseMessages(Long projectId, String question) {
        List<ContentSearchResult> hits = luceneIndexService.searchContent(projectId,
                SearchOptions.builder().query(question).limit(5).build());
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
                new AgentClient.Message("system", getSystemPrompt(projectId, systemPrompt)),
                new AgentClient.Message("user", question));
    }

    public List<AgentClient.Message> buildCodeReviewMessages(Long projectId, String filePath) throws IOException {
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
                """
                .formatted(filePath, truncated);

        return List.of(
                new AgentClient.Message("system",
                        getSystemPrompt(projectId,
                                "You are a senior software engineer conducting a detailed code review.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildCodeRefactorMessages(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String truncated = truncate(fileContent, MAX_CONTEXT_CHARS);

        String userPrompt = """
                Refactor and optimize the following code for better readability, efficiency, and performance.
                Explain your optimization decisions.
                CRITICAL INSTRUCTION - TOKEN REDUCTION:
                Do NOT output the entire refactored file. Output ONLY the lines you want to change using SEARCH/REPLACE blocks.
                Format:
                <<<< SEARCH
                [exact lines from original file]
                ==== REPLACE
                [new optimized lines]
                >>>>

                File: %s
                ```java
                %s
                ```
                """
                .formatted(filePath, truncated);

        return List.of(
                new AgentClient.Message("system",
                        getSystemPrompt(projectId, "You are a software architect specialized in code optimization.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildCodeCommitMessages(Long projectId, String diff) {
        String truncated = truncate(diff, MAX_CONTEXT_CHARS);
        String userPrompt = """
                Generate a concise, descriptive Git commit message following Conventional Commits guidelines based on the following code diff:
                ```diff
                %s
                ```
                """
                .formatted(truncated);

        return List.of(
                new AgentClient.Message("system", getSystemPrompt(projectId,
                        "You are a Git assistant. Generate clean, descriptive, and conventional commit messages.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildJavaDocMessages(Long projectId, String filePath) throws IOException {
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
                """
                .formatted(filePath, truncated);

        return List.of(
                new AgentClient.Message("system", getSystemPrompt(projectId,
                        "You are a senior Java architect. Document code following strict Javadoc specifications.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildJunitTestCasesMessages(Long projectId, String filePath) throws IOException {
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
                """
                .formatted(filePath, truncated);

        return List.of(
                new AgentClient.Message("system",
                        getSystemPrompt(projectId,
                                "You are a senior QA engineer. Generate clean, complete JUnit test suites.")),
                new AgentClient.Message("user", userPrompt));
    }

    public List<AgentClient.Message> buildWebSearchMessages(Long projectId, String query, String url, String pageText) {
        String truncated = truncate(pageText, 8000);
        String userPrompt = """
                You are an expert research analyst. Review the following web page content/search results and provide a comprehensive R&D summary.
                Focus on answering the core query or detailing the technology, specifications, or findings.
                Context:
                %s
                """
                .formatted(truncated);

        return List.of(
                new AgentClient.Message("system", getSystemPrompt(projectId,
                        "You are an expert research assistant. Synthesize web search results into a clean R&D report.")),
                new AgentClient.Message("user", userPrompt));
    }

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
            logger.warn("Could not read file for AGENT context: {}", absolutePath);
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
