package com.mcp.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.dto.AgentActionRequest;
import com.mcp.dto.BatchTaskRequest;
import com.mcp.dto.BatchTaskResponse;
import com.mcp.dto.browser.BrowserSessionRequest;
import com.mcp.entity.AgentTask;
import com.mcp.entity.AgentSubTask;
import com.mcp.model.AgentTaskStatus;
import com.mcp.properties.AgentProperties;
import com.mcp.repository.AgentTaskRepository;
import com.mcp.repository.AgentSubTaskRepository;
import com.mcp.service.AgentClient.Message;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;

@Service
public class AgentAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(AgentAsyncService.class);

    private final AgentTaskRepository agentTaskRepository;
    private final AgentSubTaskRepository agentSubTaskRepository;
    private final AgentPromptBuilder promptBuilder;
    private final WebSearchOrchestrator webSearchOrchestrator;
    private final BrowserSessionManager browserSessionManager;
    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ThreadPoolTaskExecutor executor;
    private final AgentService agentService;
    private long lastApiCallTime = 0;

    private synchronized void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastApiCallTime;
        if (elapsed < 10000) {
            try {
                Thread.sleep(10000 - Math.max(0, elapsed));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastApiCallTime = System.currentTimeMillis();
    }

    public AgentAsyncService(AgentTaskRepository agentTaskRepository,
            AgentSubTaskRepository agentSubTaskRepository,
            AgentPromptBuilder promptBuilder,
            WebSearchOrchestrator webSearchOrchestrator,
            BrowserSessionManager browserSessionManager,
            AgentProperties props,
            ObjectMapper objectMapper,
            AgentService agentService) {
        this.agentTaskRepository = agentTaskRepository;
        this.agentSubTaskRepository = agentSubTaskRepository;
        this.promptBuilder = promptBuilder;
        this.webSearchOrchestrator = webSearchOrchestrator;
        this.browserSessionManager = browserSessionManager;
        this.props = props;
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .build();
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setCorePoolSize(4);
        this.executor.setMaxPoolSize(4);
        this.executor.setQueueCapacity(1000);
        this.executor.setThreadNamePrefix("agent-task-");
        this.executor.initialize();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    public BatchTaskResponse submitBatchTasks(List<BatchTaskRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requests list cannot be empty");
        }

        Long projectId = requests.get(0).projectId();

        AgentTask mainTask = new AgentTask(projectId, "BATCH_CONSOLIDATION", "Batch orchestration task");
        mainTask = agentTaskRepository.save(mainTask);
        final Long mainTaskId = mainTask.getId();

        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (BatchTaskRequest item : requests) {
            if (item.action() == null || item.projectId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "action and projectId are required for all tasks in batch");
            }
            AgentActionRequest mergedReq = agentService.validateAndMergeParameters(
                    item.projectId(), item.action(), null, null, null, null, null, item.request());

            String reqJson = "{}";
            try {
                reqJson = objectMapper.writeValueAsString(mergedReq);
            } catch (JsonProcessingException e) {
                // ignore
            }

            AgentSubTask subTask = new AgentSubTask(mainTaskId, item.action().toLowerCase(), reqJson);
            subTask = agentSubTaskRepository.save(subTask);

            final Long subTaskId = subTask.getId();
            final String action = item.action();
            final Long itemProjectId = item.projectId();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> 
                executeSubTaskWithRetry(subTaskId, itemProjectId, action, mergedReq)
            , executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRunAsync(() -> {
            StringBuilder consolidatedResponse = new StringBuilder();
            consolidatedResponse.append("Summarize the findings of these code analyses:\n\n");
            for (int i = 0; i < futures.size(); i++) {
                try {
                    String result = futures.get(i).get();
                    consolidatedResponse.append("--- Subtask ").append(i+1).append(" ---\n");
                    consolidatedResponse.append(result).append("\n\n");
                } catch (Exception e) {
                    consolidatedResponse.append("--- Subtask ").append(i+1).append(" Failed ---\n");
                }
            }
            
            try {
                String finalResponse = sendToDefaultModel(consolidatedResponse.toString());
                AgentTask mt = agentTaskRepository.findById(mainTaskId).orElseThrow();
                mt.setTaskResponse(finalResponse);
                mt.setStatus(AgentTaskStatus.COMPLETED);
                mt.setCompletedAt(LocalDateTime.now());
                agentTaskRepository.save(mt);
            } catch (Exception e) {
                AgentTask mt = agentTaskRepository.findById(mainTaskId).orElseThrow();
                mt.setTaskResponse("Consolidation failed: " + e.getMessage());
                mt.setStatus(AgentTaskStatus.FAILED);
                mt.setCompletedAt(LocalDateTime.now());
                agentTaskRepository.save(mt);
            }
        }, executor);

        return new BatchTaskResponse(mainTaskId, "BATCH_CONSOLIDATION", mainTask.getStatus().toString());
    }

    private String executeSubTaskWithRetry(Long subTaskId, Long projectId, String action, AgentActionRequest req) {
        int maxRetries = 2;
        AgentSubTask subTask = agentSubTaskRepository.findById(subTaskId).orElse(null);
        if (subTask == null) return "SubTask not found";
        
        subTask.setStatus(AgentTaskStatus.IN_PROGRESS);
        agentSubTaskRepository.save(subTask);
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String result = executeSingleTaskLogic(projectId, action, req);
                subTask.setTaskResponse(result);
                subTask.setStatus(AgentTaskStatus.COMPLETED);
                subTask.setCompletedAt(LocalDateTime.now());
                agentSubTaskRepository.save(subTask);
                return result;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    logger.error("Subtask {} failed after {} retries", subTaskId, maxRetries, e);
                    subTask.setStatus(AgentTaskStatus.FAILED);
                    String errorMsg = "Error: Subtask " + subTaskId + " failed: " + e.getMessage();
                    subTask.setTaskResponse(errorMsg);
                    subTask.setCompletedAt(LocalDateTime.now());
                    agentSubTaskRepository.save(subTask);
                    return errorMsg;
                }
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        return "Failed";
    }

    private String executeSingleTaskLogic(Long projectId, String action, AgentActionRequest req) throws Exception {
        String sessionId = null;
        try {
            List<Message> messages;
            String model;
            switch (action.toLowerCase()) {
                case "explain-symbol" -> {
                    messages = promptBuilder.buildExplainSymbolMessages(projectId, req.symbolId());
                    model = props.getModelCodeAnalysis();
                }
                case "explain-file" -> {
                    messages = promptBuilder.buildExplainFileMessages(projectId, req.filePath());
                    model = props.getModelCodeAnalysis();
                }
                case "ask" -> {
                    messages = promptBuilder.buildAskCodebaseMessages(projectId, req.question());
                    model = props.getModelCodeAnalysis();
                }
                case "code-review" -> {
                    messages = promptBuilder.buildCodeReviewMessages(projectId, req.filePath());
                    model = props.getModelCodeReview();
                }
                case "code-refactor", "code-optimise" -> {
                    messages = promptBuilder.buildCodeRefactorMessages(projectId, req.filePath());
                    model = props.getModelCodeRefactor();
                }
                case "web-search" -> {
                    sessionId = browserSessionManager
                            .createSession(new BrowserSessionRequest("chromium", true, null, null, projectId));
                    String pageText = webSearchOrchestrator.fetchWebSearchContent(req.query(), req.url(), sessionId);
                    messages = promptBuilder.buildWebSearchMessages(projectId, req.query(), req.url(), pageText);
                    model = props.getModelWebSearch();
                }
                case "code-commit" -> {
                    messages = promptBuilder.buildCodeCommitMessages(projectId, req.diff());
                    model = props.getModelCodeCommit();
                }
                case "java-doc" -> {
                    messages = promptBuilder.buildJavaDocMessages(projectId, req.filePath());
                    model = props.getModelJavaDoc();
                }
                case "junit-test-cases" -> {
                    messages = promptBuilder.buildJunitTestCasesMessages(projectId, req.filePath());
                    model = props.getModelJunitTest();
                }
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            }

            if (model == null) {
                model = props.getDefaultModel();
            }

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.7);
            payload.put("max_tokens", props.getMaxTokens());

            ArrayNode messagesArray = payload.putArray("messages");
            for (Message m : messages) {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", m.role());
                msgNode.put("content", m.content());
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            enforceRateLimit();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP Error " + response.statusCode() + ": " + response.body());
            }

            return parseChatCompletionResponse(response.body());
        } finally {
            if (sessionId != null) {
                browserSessionManager.closeSession(sessionId);
            }
        }
    }

    private String sendToDefaultModel(String prompt) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", props.getDefaultModel());
        payload.put("temperature", 0.7);
        payload.put("max_tokens", props.getMaxTokens());

        ArrayNode messagesArray = payload.putArray("messages");
        ObjectNode msgNode = messagesArray.addObject();
        msgNode.put("role", "user");
        msgNode.put("content", prompt);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        enforceRateLimit();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP Error " + response.statusCode() + ": " + response.body());
        }

        return parseChatCompletionResponse(response.body());
    }

    private String parseChatCompletionResponse(String json) {
        try {
            var node = objectMapper.readTree(json);
            var choices = node.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                var message = choices.get(0).get("message");
                if (message != null) {
                    var content = message.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse response JSON", e);
        }
        return "";
    }

    public AgentTask getTask(Long id) {
        return agentTaskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AgentTask not found: " + id));
    }

    public List<AgentTask> getTasksByProjectId(Long projectId) {
        return agentTaskRepository.findByProjectId(projectId);
    }
}
