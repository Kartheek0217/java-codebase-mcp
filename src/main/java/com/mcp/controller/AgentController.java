package com.mcp.controller;

import com.mcp.dto.AgentActionRequest;
import com.mcp.dto.BatchTaskResponse;
import com.mcp.entity.AgentTask;
import com.mcp.service.AgentAsyncService;
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
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import com.mcp.dto.SyncResponse;
import io.github.overrridee.annotation.ResponseEnvelope;
import io.github.overrridee.annotation.IgnoreEnvelope;

/**
 * Unified REST controller for all OpenAI-compatible / OpenAdapter AGENT
 * operations, including background asynchronous tasks.
 */
@RestController
@RequestMapping("/api/agent")
@Validated
@Tag(name = "AGENT", description = "Unified OpenAI-compatible cloud AGENT endpoints for AI assistance.")
public class AgentController {

    private final AgentService agentService;
    private final AgentAsyncService agentAsyncService;

    public AgentController(AgentService agentService, AgentAsyncService agentAsyncService) {
        this.agentService = agentService;
        this.agentAsyncService = agentAsyncService;
    }

    // ─── Synchronous and Streaming Endpoints ─────────────────────────────────

    @PostMapping(value = "/explain-symbol", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "explain_symbol", description = "Explain a code symbol in plain English. Params: symbolId")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter explainSymbol(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "explain-symbol", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "explain-symbol", mergedReq);
    }

    @PostMapping(value = "/explain-symbol/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse explainSymbolSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "explain-symbol", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "explain-symbol", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/explain-file", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "explain_file", description = "Explain what a source file does. Params: filePath")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter explainFile(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "explain-file", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "explain-file", mergedReq);
    }

    @PostMapping(value = "/explain-file/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse explainFileSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "explain-file", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "explain-file", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "ask_question", description = "Ask a free-form question about the codebase. Body: {question}")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter askQuestion(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "ask", symbolId, filePath,
                query, url, diff, request);
        return agentService.streamResponse(projectId, "ask", mergedReq);
    }

    @PostMapping(value = "/ask/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse askQuestionSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "ask", symbolId, filePath,
                query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "ask", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/code-review", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "code_review", description = "Generate a code review for a file. Params: filePath")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter codeReview(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-review", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "code-review", mergedReq);
    }

    @PostMapping(value = "/code-review/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse codeReviewSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-review", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "code-review", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/code-refactor", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "code_refactor", description = "Suggest refactoring improvements for a file. Params: filePath")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter codeRefactor(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-refactor", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "code-refactor", mergedReq);
    }

    @PostMapping(value = "/code-refactor/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse codeRefactorSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-refactor", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "code-refactor", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/code-optimise", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "code_optimise", description = "Suggest performance optimisations for a file. Params: filePath")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter codeOptimise(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-optimise", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "code-optimise", mergedReq);
    }

    @PostMapping(value = "/code-optimise/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse codeOptimiseSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-optimise", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "code-optimise", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/web-search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "web_search", description = "Search the web and summarise results. Params: query or url")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter webSearch(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "web-search", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "web-search", mergedReq);
    }

    @PostMapping(value = "/web-search/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse webSearchSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "web-search", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "web-search", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/code-commit", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "code_commit", description = "Generate a Conventional Commits message from a git diff. Params: diff")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter codeCommit(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-commit", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "code-commit", mergedReq);
    }

    @PostMapping(value = "/code-commit/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse codeCommitSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "code-commit", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "code-commit", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/java-doc", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "java_doc", description = "Generate Javadoc for all public methods in a file. Params: filePath")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter javaDoc(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "java-doc", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "java-doc", mergedReq);
    }

    @PostMapping(value = "/java-doc/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse javaDocSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "java-doc", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "java-doc", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    @PostMapping(value = "/junit-test-cases", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "junit_test_cases", description = "Generate JUnit 5 test class with 100% branch coverage. Params: filePath")
    @IgnoreEnvelope(reason = "SSE streaming")
    public SseEmitter junitTestCases(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "junit-test-cases", symbolId,
                filePath, query, url, diff, request);
        return agentService.streamResponse(projectId, "junit-test-cases", mergedReq);
    }

    @PostMapping(value = "/junit-test-cases/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseEnvelope
    public SyncResponse junitTestCasesSync(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        AgentActionRequest mergedReq = agentService.validateAndMergeParameters(projectId, "junit-test-cases", symbolId,
                filePath, query, url, diff, request);
        try {
            String responseText = agentService.syncResponse(projectId, "junit-test-cases", mergedReq);
            return new SyncResponse(responseText);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute AGENT action", ex);
        }
    }

    // ─── Asynchronous Task Endpoints ────────────────────────────────────────

    @PostMapping(value = "/async-task/submit/{action}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "submit-agent-task", description = "Submit an agent action to run in the background. Returns taskId.")
    public Map<String, Object> submitTask(
            @PathVariable String action,
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @Valid @RequestBody(required = false) AgentActionRequest request) {
        return agentAsyncService.submitTask(action, projectId, symbolId, filePath, query, url, diff, request);
    }

    @PostMapping(value = "/async-task/submit-batch", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "submit-batch-agent-tasks", description = "Submit multiple agent actions to run in the background in parallel. Returns a list of taskIds.")
    @ResponseEnvelope
    public List<BatchTaskResponse> submitBatchTasks(@RequestBody String rawPayload) {
        return agentAsyncService.submitBatchTasks(rawPayload);
    }

    @GetMapping(value = "/async-task/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "get-agent-task", description = "Retrieve the status and response of a background task.")
    @ResponseEnvelope
    public AgentTask getTask(@PathVariable Long id) {
        return agentAsyncService.getTask(id);
    }

    @GetMapping(value = "/async-task", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "get-agent-tasks", description = "Retrieve all background tasks associated with a project.")
    @ResponseEnvelope
    public List<AgentTask> getTasks(
            @Parameter(description = "Numeric Project ID") @RequestHeader(required = true) Long projectId) {
        return agentAsyncService.getTasks(projectId);
    }
}