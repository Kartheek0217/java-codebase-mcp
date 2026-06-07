package com.mcp.controller;

import com.mcp.dto.LlmActionRequest;
import com.mcp.repository.ProjectRepository;
import com.mcp.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unified REST controller for all OpenAI-compatible / OpenAdapter LLM
 * operations.
 *
 * @author karthik.j
 */
@RestController
@RequestMapping("/api/llm")
@Tag(name = "LLM", description = "Unified OpenAI-compatible cloud LLM endpoints for AI assistance.")
public class LlmController {

    /** Service layer for executing LLM operations and managing response streams. */
    private final LlmService llmService;

    /** Repository for project data access and security validation. */
    private final ProjectRepository projectRepository;

    /**
     * Constructs an {@code LlmController} with required dependencies.
     *
     * @param llmService        the service for executing LLM operations
     * @param projectRepository the repository for project data access
     */
    public LlmController(LlmService llmService, ProjectRepository projectRepository) {
        this.llmService = llmService;
        this.projectRepository = projectRepository;
    }

    /**
     * Consolidates explain-symbol, explain-file, ask, code-review, code-refactor,
     * and web-search actions.
     * Validates input parameters based on the requested action and streams the
     * response chunks back to the client.
     *
     * @param projectId the unique identifier of the project context
     * @param action    the LLM action to execute (e.g., 'explain-symbol', 'ask',
     *                  'code-review')
     * @param symbolId  optional identifier for symbol explanation
     * @param filePath  optional file path for file-based operations
     * @param query     optional search query for web-search
     * @param url       optional URL for web-search context
     * @param diff      optional git diff payload for code-commit action
     * @param request   optional request body containing action-specific parameters
     * @return an {@link SseEmitter} for streaming the LLM response chunks
     * @throws ResponseStatusException if validation fails, required parameters are
     *                                 missing, or an unknown action is provided
     */
    @PostMapping(value = "/{projectId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "llm-action", description = "Executes various LLM operations based on X-Action header and streams response chunks.")
    public SseEmitter handleLlmAction(
            @PathVariable Long projectId,
            @Parameter(description = "LLM action: 'explain-symbol', 'explain-file', 'ask', 'code-review', 'code-refactor', 'web-search', 'code-commit', 'java-doc', 'junit-test-cases'") @RequestHeader(value = "X-Action") String action,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @RequestBody(required = false) LlmActionRequest request) {

        LlmActionRequest req = request != null ? request : new LlmActionRequest(null, null, null, null, null, null);

        // Validation upfront to fail-fast
        try {
            switch (action.toLowerCase()) {
                case "explain-symbol" -> {
                    Long sId = symbolId != null ? symbolId : req.symbolId();
                    if (sId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "symbolId is required for explain-symbol");
                    }
                }
                case "explain-file" -> {
                    String path = filePath != null ? filePath : req.filePath();
                    if (path == null || path.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "filePath is required for explain-file");
                    }
                    validateFilePath(projectId, path);
                }
                case "ask" -> {
                    String question = req.question();
                    if (question == null || question.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "question is required in request body");
                    }
                }
                case "code-review" -> {
                    String path = filePath != null ? filePath : req.filePath();
                    if (path == null || path.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "filePath is required for code-review");
                    }
                    validateFilePath(projectId, path);
                }
                case "code-refactor", "code-optimise" -> {
                    String path = filePath != null ? filePath : req.filePath();
                    if (path == null || path.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "filePath is required for code-refactor");
                    }
                    validateFilePath(projectId, path);
                }
                case "web-search" -> {
                    String q = query != null ? query : req.query();
                    String u = url != null ? url : req.url();
                    if ((q == null || q.isBlank()) && (u == null || u.isBlank())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Either query or url must be provided for web-search");
                    }
                }
                case "code-commit" -> {
                    String d = diff != null ? diff : req.diff();
                    if (d == null || d.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diff is required for code-commit");
                    }
                }
                case "java-doc" -> {
                    String path = filePath != null ? filePath : req.filePath();
                    if (path == null || path.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filePath is required for java-doc");
                    }
                    validateFilePath(projectId, path);
                }
                case "junit-test-cases" -> {
                    String path = filePath != null ? filePath : req.filePath();
                    if (path == null || path.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "filePath is required for junit-test-cases");
                    }
                    validateFilePath(projectId, path);
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown LLM action: " + action);
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Validation failed", ex);
        }

        LlmActionRequest mergedReq = new LlmActionRequest(
                symbolId != null ? symbolId : req.symbolId(),
                filePath != null ? filePath : req.filePath(),
                query != null ? query : req.query(),
                url != null ? url : req.url(),
                diff != null ? diff : req.diff(),
                req.question());

        return llmService.streamResponse(projectId, action, mergedReq);
    }

    /**
     * Validates that the given file path is secure and belongs to the specified
     * project.
     * Prevents directory traversal attacks by normalizing the path.
     *
     * @param projectId the unique identifier of the project
     * @param filePath  the relative or absolute file path to validate
     * @throws ResponseStatusException if the file path is invalid or attempts
     *                                 directory traversal
     */
    private void validateFilePath(Long projectId, String filePath) {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId))
                .getRootPath();
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path target = root.resolve(filePath).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path: path traversal detected");
        }
    }
}