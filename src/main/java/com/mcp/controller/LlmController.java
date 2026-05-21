package com.mcp.controller;

import com.mcp.properties.OllamaProperties;
import com.mcp.service.LlmService;
import com.mcp.service.OllamaClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

/**
 * REST endpoints that expose Ollama LLM capabilities over the indexed codebase.
 * <p>
 * All endpoints are NEW and additive — no existing endpoints are modified.
 * Base path: {@code /api/llm/{projectId}}
 * </p>
 *
 * <ul>
 * <li>{@code POST /api/llm/{projectId}/explain-symbol?symbolId=} — explain a
 * Java symbol</li>
 * <li>{@code POST /api/llm/{projectId}/explain-file?filePath=} — summarise a
 * source file</li>
 * <li>{@code POST /api/llm/{projectId}/ask} — free-form Q&A with codebase
 * context</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/llm")
@Tag(name = "LLM", description = "Ollama / OpenAI-compatible LLM endpoints for AI-assisted codebase analysis.")
public class LlmController {

    private final LlmService llmService;
    private final OllamaProperties ollamaProps;

    public LlmController(LlmService llmService, OllamaProperties ollamaProps) {
        this.llmService = llmService;
        this.ollamaProps = ollamaProps;
    }

    // -------------------------------------------------------------------------
    // Endpoint: Explain Symbol
    // -------------------------------------------------------------------------

    /**
     * Asks the LLM to explain what a specific Java symbol (class, method, field)
     * does.
     *
     * <p>
     * Example:
     * 
     * <pre>
     * POST /api/llm/1/explain-symbol?symbolId=42
     * </pre>
     *
     * @param projectId project that owns the symbol
     * @param symbolId  database ID of the symbol (from
     *                  {@code /api/codebase/{projectId}/symbols})
     * @return JSON with the LLM answer and request metadata
     */
    @PostMapping("/{projectId}/explain-symbol")
    @Operation(summary = "explain-symbol", description = "Uses the LLM to explain what a Java symbol (class/method/field) does, "
            + "using its source code as context. Provide the symbolId from the symbols search endpoint.")
    public Map<String, Object> explainSymbol(
            @PathVariable Long projectId,
            @RequestParam Long symbolId) {
        try {
            String answer = llmService.explainSymbol(projectId, symbolId);
            return buildResponse(projectId, answer);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (OllamaClient.OllamaException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "LLM request failed: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Endpoint: Explain File
    // -------------------------------------------------------------------------

    /**
     * Asks the LLM to summarise an entire source file in plain English.
     * Method bodies are stripped before sending to the LLM to reduce token usage
     * while preserving all declarations, signatures, and Javadocs.
     *
     * <p>
     * Example:
     * 
     * <pre>
     * POST /api/llm/1/explain-file?filePath=src/main/java/com/mcp/service/ProjectService.java
     * </pre>
     *
     * @param projectId project that owns the file
     * @param filePath  path relative to the project root
     * @return JSON with the LLM answer and request metadata
     */
    @PostMapping("/{projectId}/explain-file")
    @Operation(summary = "explain-file", description = "Uses the LLM to produce a plain-English summary of a source file. "
            + "Provide filePath relative to the project root.")
    public Map<String, Object> explainFile(
            @PathVariable Long projectId,
            @RequestParam String filePath) {
        try {
            String answer = llmService.explainFile(projectId, filePath);
            return buildResponse(projectId, answer);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "File not found or unreadable: " + filePath);
        } catch (OllamaClient.OllamaException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "LLM request failed: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Endpoint: Ask Codebase
    // -------------------------------------------------------------------------

    /**
     * Free-form Q&A about the codebase.
     * <p>
     * Lucene searches the indexed project for code snippets relevant to the
     * question
     * and injects them into the LLM system prompt as context before generating the
     * answer.
     * </p>
     *
     * <p>
     * Example request body:
     * 
     * <pre>
     * { "question": "How does file indexing work?" }
     * </pre>
     *
     * @param projectId project to search within
     * @param body      JSON object with a {@code "question"} field
     * @return JSON with the LLM answer and request metadata
     */
    @PostMapping("/{projectId}/ask")
    @Operation(summary = "ask", description = "Ask a free-form question about the codebase. "
            + "Relevant code snippets are automatically retrieved from the Lucene index and "
            + "injected into the LLM prompt as context. "
            + "Request body: { \"question\": \"your question here\" }")
    public Map<String, Object> ask(
            @PathVariable Long projectId,
            @RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request body must contain a non-empty 'question' field.");
        }
        try {
            String answer = llmService.askCodebase(projectId, question);
            return buildResponse(projectId, answer);
        } catch (OllamaClient.OllamaException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "LLM request failed: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Map<String, Object> buildResponse(Long projectId, String answer) {
        return Map.of(
                "projectId", projectId,
                "model", ollamaProps.getModel(),
                "answer", answer);
    }
}
