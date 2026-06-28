package com.mcp.controller;

import com.mcp.dto.AgentActionRequest;
import com.mcp.dto.BatchTaskResponse;
import com.mcp.dto.TaskSubmission;
import com.mcp.entity.AgentTask;
import com.mcp.service.AgentAsyncService;
import com.mcp.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;

import java.util.List;


import org.springframework.validation.annotation.Validated;
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

    // ─── Asynchronous Task Endpoints ────────────────────────────────────────

    @PostMapping(value = "/async-task/submit", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "submit-agent-task", description = "Submit agent actions to run in the background. Handles both single and batch requests. Returns a list of taskIds.")
    @ResponseEnvelope
    public BatchTaskResponse submitTasks(@RequestBody TaskSubmission payload) {
        return agentAsyncService.submitBatchTasks(payload.requests());
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
        return agentAsyncService.getTasksByProjectId(projectId);
    }
}