package com.mcp.controller;

import com.mcp.dto.AgentActionRequest;
import com.mcp.repository.ProjectRepository;
import com.mcp.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


/**
 * Unified REST controller for all OpenAI-compatible / OpenAdapter AGENT
 * operations.
 *
 * @author karthik.j
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "AGENT", description = "Unified OpenAI-compatible cloud AGENT endpoints for AI assistance.")
public class AgentController {

    /** Service layer for executing AGENT operations and managing response streams. */
    private final AgentService agentService;

    /** Repository for project data access and security validation. */
    private final ProjectRepository projectRepository;

    /**
     * Constructs an {@code AgentController} with required dependencies.
     *
     * @param agentService        the service for executing AGENT operations
     * @param projectRepository the repository for project data access
     */
    public AgentController(AgentService agentService, ProjectRepository projectRepository) {
        this.agentService = agentService;
        this.projectRepository = projectRepository;
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
        com.mcp.util.PathSecurityUtil.validateAndNormalizePath(rootPath, filePath);
    }

    private AgentActionRequest validateAndMergeParameters(Long projectId, String action, Long symbolId, String filePath, String query, String url, String diff, AgentActionRequest request) {
        AgentActionRequest req = request != null ? request : new AgentActionRequest(null, null, null, null, null, null);

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
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action. Allowed: explain-symbol, explain-file, ask, code-review, ...");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Validation failed", ex);
        }

        return new AgentActionRequest(
                symbolId != null ? symbolId : req.symbolId(),
                filePath != null ? filePath : req.filePath(),
                query != null ? query : req.query(),
                url != null ? url : req.url(),
                diff != null ? diff : req.diff(),
                req.question());
    }
	@PostMapping(value = "/explain-symbol", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "explain_symbol", description = "Explain a code symbol in plain English. Params: symbolId")
	public SseEmitter explainSymbol(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "explain-symbol", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "explain-symbol", mergedReq);
	}

	@PostMapping(value = "/explain-symbol/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> explainSymbolSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "explain-symbol", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "explain-symbol", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/explain-file", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "explain_file", description = "Explain what a source file does. Params: filePath")
	public SseEmitter explainFile(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "explain-file", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "explain-file", mergedReq);
	}

	@PostMapping(value = "/explain-file/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> explainFileSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "explain-file", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "explain-file", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "ask_question", description = "Ask a free-form question about the codebase. Body: {question}")
	public SseEmitter askQuestion(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "ask", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "ask", mergedReq);
	}

	@PostMapping(value = "/ask/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> askQuestionSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "ask", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "ask", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/code-review", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "code_review", description = "Generate a code review for a file. Params: filePath")
	public SseEmitter codeReview(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-review", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "code-review", mergedReq);
	}

	@PostMapping(value = "/code-review/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> codeReviewSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-review", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "code-review", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/code-refactor", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "code_refactor", description = "Suggest refactoring improvements for a file. Params: filePath")
	public SseEmitter codeRefactor(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-refactor", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "code-refactor", mergedReq);
	}

	@PostMapping(value = "/code-refactor/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> codeRefactorSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-refactor", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "code-refactor", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/code-optimise", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "code_optimise", description = "Suggest performance optimisations for a file. Params: filePath")
	public SseEmitter codeOptimise(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-optimise", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "code-optimise", mergedReq);
	}

	@PostMapping(value = "/code-optimise/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> codeOptimiseSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-optimise", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "code-optimise", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/web-search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "web_search", description = "Search the web and summarise results. Params: query or url")
	public SseEmitter webSearch(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "web-search", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "web-search", mergedReq);
	}

	@PostMapping(value = "/web-search/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> webSearchSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "web-search", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "web-search", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/code-commit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "code_commit", description = "Generate a Conventional Commits message from a git diff. Params: diff")
	public SseEmitter codeCommit(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-commit", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "code-commit", mergedReq);
	}

	@PostMapping(value = "/code-commit/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> codeCommitSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "code-commit", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "code-commit", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/java-doc", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "java_doc", description = "Generate Javadoc for all public methods in a file. Params: filePath")
	public SseEmitter javaDoc(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "java-doc", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "java-doc", mergedReq);
	}

	@PostMapping(value = "/java-doc/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> javaDocSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "java-doc", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "java-doc", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

	@PostMapping(value = "/junit-test-cases", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "junit_test_cases", description = "Generate JUnit 5 test class with 100% branch coverage. Params: filePath")
	public SseEmitter junitTestCases(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "junit-test-cases", symbolId, filePath, query, url, diff, request);
		return agentService.streamResponse(projectId, "junit-test-cases", mergedReq);
	}

	@PostMapping(value = "/junit-test-cases/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public java.util.Map<String, String> junitTestCasesSync(
			@Parameter(description = "Numeric Project ID") @RequestHeader("projectId") Long projectId,
			@RequestParam(required = false) Long symbolId,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String diff,
			@RequestBody(required = false) AgentActionRequest request) {
		AgentActionRequest mergedReq = validateAndMergeParameters(projectId, "junit-test-cases", symbolId, filePath, query, url, diff, request);
		try {
			String responseText = agentService.syncResponse(projectId, "junit-test-cases", mergedReq);
			return java.util.Map.of("response", responseText);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
		}
	}

}