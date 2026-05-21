package com.mcp.service;

import com.mcp.dto.ContentSearchResult;
import com.mcp.entity.Symbol;
import com.mcp.properties.OllamaProperties;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * High-level LLM service that composes {@link OllamaClient} with the existing
 * indexed codebase data to power three AI features:
 * <ul>
 * <li><b>explainSymbol</b> — explain what a Java symbol (class/method/field)
 * does</li>
 * <li><b>explainFile</b> — summarise a source file in plain English</li>
 * <li><b>askCodebase</b> — free-form Q&A with Lucene-retrieved code
 * context</li>
 * </ul>
 *
 * <p>
 * No existing endpoints or services are modified. This service is additive
 * only.
 * </p>
 */
@Service
public class LlmService {

    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);

    // Maximum characters of source code injected into a single prompt.
    // Keeps token usage predictable regardless of file size.
    private static final int MAX_CONTEXT_CHARS = 6_000;

    private final OllamaClient ollamaClient;
    private final SymbolRepository symbolRepository;
    private final ProjectRepository projectRepository;
    private final CodeSummarizerService codeSummarizerService;
    private final LuceneIndexService luceneIndexService;

    public LlmService(OllamaClient ollamaClient,
            OllamaProperties ollamaProps,
            SymbolRepository symbolRepository,
            ProjectRepository projectRepository,
            FileIndexerService fileIndexerService,
            CodeSummarizerService codeSummarizerService,
            LuceneIndexService luceneIndexService) {
        this.ollamaClient = ollamaClient;
        this.symbolRepository = symbolRepository;
        this.projectRepository = projectRepository;
        this.codeSummarizerService = codeSummarizerService;
        this.luceneIndexService = luceneIndexService;
    }

    // -------------------------------------------------------------------------
    // Feature: Explain Symbol
    // -------------------------------------------------------------------------

    /**
     * Explains what a specific Java symbol (class, method, field) does by reading
     * its source file, extracting the structural skeleton and asking the LLM.
     *
     * @param projectId project containing the symbol
     * @param symbolId  database ID of the symbol
     * @return LLM explanation as plain text
     */
    public String explainSymbol(Long projectId, Long symbolId) {
        Symbol symbol = symbolRepository.findById(symbolId)
                .orElseThrow(() -> new IllegalArgumentException("Symbol not found: " + symbolId));

        String fileContent = readFileSafe(symbol.getFilePath());
        String structure = codeSummarizerService.extractStructure(fileContent);
        String truncated = truncate(structure, MAX_CONTEXT_CHARS);

        String userPrompt = """
                You are an expert Java developer. Explain the following code component clearly and concisely.
                Focus on: what it does, its responsibilities, and any important patterns it uses.

                Symbol: %s (%s)
                File: %s

                Source code (structural skeleton):
                ```java
                %s
                ```

                Provide a plain-English explanation suitable for a developer unfamiliar with this codebase.
                """.formatted(symbol.getName(), symbol.getType(), symbol.getFilePath(), truncated);

        List<OllamaClient.Message> messages = List.of(
                new OllamaClient.Message("system",
                        "You are a senior Java code analyst. Answer concisely and accurately."),
                new OllamaClient.Message("user", userPrompt));

        logger.info("LLM explainSymbol — project={}, symbol={} ({})", projectId, symbol.getName(), symbolId);
        return ollamaClient.chat(messages);
    }

    // -------------------------------------------------------------------------
    // Feature: Explain File
    // -------------------------------------------------------------------------

    /**
     * Produces a plain-English summary of an entire source file.
     * Uses {@link CodeSummarizerService#extractStructure(String)} to strip method
     * bodies before sending to the LLM — reducing token usage while preserving
     * all declarations, signatures, and Javadocs.
     *
     * @param projectId project containing the file
     * @param filePath  path relative to the project root
     * @return LLM summary as plain text
     * @throws IOException if the file cannot be read
     */
    public String explainFile(Long projectId, String filePath) throws IOException {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId))
                .getRootPath();

        String absolutePath = Paths.get(rootPath).resolve(filePath).toAbsolutePath().toString();
        String fileContent = Files.readString(Paths.get(absolutePath));
        String structure = codeSummarizerService.extractStructure(fileContent);
        String truncated = truncate(structure, MAX_CONTEXT_CHARS);

        String userPrompt = """
                You are an expert Java developer. Analyse the following Java source file and provide:
                1. A one-paragraph high-level summary of the file's purpose.
                2. A bullet list of the key classes, interfaces, or methods and what each does.
                3. Any notable design patterns or architectural concerns.

                File: %s

                ```java
                %s
                ```
                """.formatted(filePath, truncated);

        List<OllamaClient.Message> messages = List.of(
                new OllamaClient.Message("system",
                        "You are a senior Java code analyst. Answer concisely and accurately."),
                new OllamaClient.Message("user", userPrompt));

        logger.info("LLM explainFile — project={}, file={}", projectId, filePath);
        return ollamaClient.chat(messages);
    }

    // -------------------------------------------------------------------------
    // Feature: Ask Codebase (Q&A with Lucene context injection)
    // -------------------------------------------------------------------------

    /**
     * Answers a free-form question about the codebase.
     * <p>
     * Strategy: Lucene searches the indexed codebase for snippets relevant to the
     * question, injects them into the system prompt as context, then asks the LLM
     * to answer based on that context.
     * </p>
     *
     * @param projectId project to search within
     * @param question  user's natural-language question
     * @return LLM answer as plain text
     */
    public String askCodebase(Long projectId, String question) {
        // 1. Retrieve relevant code snippets from Lucene
        List<ContentSearchResult> hits = luceneIndexService.searchContent(projectId, question, 5);

        // 2. Build a context block from the top search results
        String context = buildContextBlock(hits);

        String systemPrompt = """
                You are an expert Java developer assisting with codebase questions.
                You have been given relevant code snippets from the project's indexed codebase as context.
                Use the context to answer the user's question accurately. If the context doesn't contain
                enough information, say so clearly. Do not invent implementation details.

                === CODEBASE CONTEXT ===
                %s
                === END CONTEXT ===
                """.formatted(context.isEmpty() ? "(no relevant snippets found)" : context);

        List<OllamaClient.Message> messages = List.of(
                new OllamaClient.Message("system", systemPrompt),
                new OllamaClient.Message("user", question));

        logger.info("LLM askCodebase — project={}, question length={}, context snippets={}",
                projectId, question.length(), hits.size());
        return ollamaClient.chat(messages);
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
