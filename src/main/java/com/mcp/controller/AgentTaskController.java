package com.mcp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.dto.AgentActionRequest;
import com.mcp.entity.AgentTask;
import com.mcp.repository.AgentTaskRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.service.AgentAsyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.BatchTaskRequest;
import com.mcp.dto.BatchTaskResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/task")
@Tag(name = "AGENT TASK", description = "Background asynchronous AGENT operations.")
public class AgentTaskController {

    private final AgentAsyncService agentAsyncService;
    private final AgentTaskRepository agentTaskRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public AgentTaskController(AgentAsyncService agentAsyncService,
            AgentTaskRepository agentTaskRepository,
            ProjectRepository projectRepository,
            ObjectMapper objectMapper) {
        this.agentAsyncService = agentAsyncService;
        this.agentTaskRepository = agentTaskRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

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

    private AgentActionRequest validateAndMergeParameters(Long projectId, String action, Long symbolId, String filePath,
            String query, String url, String diff, AgentActionRequest request) {
        AgentActionRequest req = request != null ? request : new AgentActionRequest(null, null, null, null, null, null);
        try {
            switch (action.toLowerCase()) {
                case "explain-symbol" -> {
                    if ((symbolId != null ? symbolId : req.symbolId()) == null)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbolId is required");
                }
                case "explain-file", "code-review", "code-refactor", "code-optimise", "java-doc",
                        "junit-test-cases" -> {
                    String path = filePath != null ? filePath : req.filePath();
                    if (path == null || path.isBlank())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filePath is required");
                    validateFilePath(projectId, path);
                }
                case "ask" -> {
                    String question = req.question();
                    if (question == null || question.isBlank())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
                }
                case "web-search" -> {
                    String q = query != null ? query : req.query();
                    String u = url != null ? url : req.url();
                    if ((q == null || q.isBlank()) && (u == null || u.isBlank()))
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query or url must be provided");
                }
                case "code-commit" -> {
                    String d = diff != null ? diff : req.diff();
                    if (d == null || d.isBlank())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diff is required");
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action");
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

    @PostMapping(value = "/submit/{action}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit async task", description = "Submit an agent action to run in the background. Returns taskId.")
    public Map<String, Object> submitTask(
            @PathVariable String action,
            @Parameter(description = "Numeric Project ID") @RequestParam Long projectId,
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String diff,
            @RequestBody(required = false) AgentActionRequest request) {

        AgentActionRequest mergedReq = validateAndMergeParameters(projectId, action, symbolId, filePath, query, url,
                diff, request);

        String reqJson = "{}";
        try {
            reqJson = objectMapper.writeValueAsString(mergedReq);
        } catch (JsonProcessingException e) {
            // ignore
        }

        AgentTask task = new AgentTask(projectId, action.toLowerCase(), reqJson);
        task = agentTaskRepository.save(task);

        agentAsyncService.executeAsyncTask(task.getId(), projectId, action, mergedReq);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", task.getId());
        response.put("status", task.getStatus());
        return response;
    }

    @PostMapping(value = "/submit-batch", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit batch async tasks", description = "Submit multiple agent actions to run in the background in parallel. Returns a list of taskIds.")
    public List<BatchTaskResponse> submitBatchTasks(@RequestBody List<BatchTaskRequest> requests) {
        List<BatchTaskResponse> responses = new ArrayList<>();
        for (BatchTaskRequest item : requests) {
            if (item.action() == null || item.projectId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "action and projectId are required for all tasks in batch");
            }
            AgentActionRequest mergedReq = validateAndMergeParameters(
                    item.projectId(),
                    item.action(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    item.request());

            String reqJson = "{}";
            try {
                reqJson = objectMapper.writeValueAsString(mergedReq);
            } catch (JsonProcessingException e) {
                // ignore
            }

            AgentTask task = new AgentTask(item.projectId(), item.action().toLowerCase(), reqJson);
            task = agentTaskRepository.save(task);

            agentAsyncService.executeAsyncTask(task.getId(), item.projectId(), item.action(), mergedReq);

            responses.add(new BatchTaskResponse(task.getId(), item.action(), task.getStatus().toString()));
        }
        return responses;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get task status", description = "Retrieve the status and response of a background task.")
    public AgentTask getTask(@PathVariable Long id) {
        return agentTaskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AgentTask not found: " + id));
    }
}
