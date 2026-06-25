package com.mcp.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.dto.AgentActionRequest;
import com.mcp.dto.browser.BrowserSessionRequest;
import com.mcp.entity.AgentTask;
import com.mcp.model.AgentTaskStatus;
import com.mcp.properties.AgentProperties;
import com.mcp.repository.AgentTaskRepository;
import com.mcp.service.AgentClient.Message;

import jakarta.annotation.PreDestroy;

@Service
public class AgentAsyncService {

    private static final Logger logger = LoggerFactory.getLogger(AgentAsyncService.class);

    private final AgentTaskRepository agentTaskRepository;
    private final AgentPromptBuilder promptBuilder;
    private final WebSearchOrchestrator webSearchOrchestrator;
    private final BrowserSessionManager browserSessionManager;
    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ThreadPoolTaskExecutor executor;

    public AgentAsyncService(AgentTaskRepository agentTaskRepository,
            AgentPromptBuilder promptBuilder,
            WebSearchOrchestrator webSearchOrchestrator,
            BrowserSessionManager browserSessionManager,
            AgentProperties props,
            ObjectMapper objectMapper) {
        this.agentTaskRepository = agentTaskRepository;
        this.promptBuilder = promptBuilder;
        this.webSearchOrchestrator = webSearchOrchestrator;
        this.browserSessionManager = browserSessionManager;
        this.props = props;
        this.objectMapper = objectMapper;
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

    public void executeAsyncTask(Long taskId, Long projectId, String action, AgentActionRequest req) {
        executor.submit(() -> executeAsyncTaskInternal(taskId, projectId, action, req));
    }

    private void executeAsyncTaskInternal(Long taskId, Long projectId, String action, AgentActionRequest req) {
        AgentTask task = agentTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            logger.error("AgentTask not found: {}", taskId);
            return;
        }

        String sessionId = null;
        try {
            task.setStatus(AgentTaskStatus.IN_PROGRESS);
            agentTaskRepository.save(task);

            logger.info("Executing background task {} for project {} using direct REST API", taskId, projectId);

            // 1. Build messages
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

            // 2. Build JSON payload
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

            // 3. Make direct REST API call
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP Error " + response.statusCode() + ": " + response.body());
            }

            // 4. Parse response
            String content = parseChatCompletionResponse(response.body());

            // 5. Update task
            task.setTaskResponse(content);
            task.setStatus(AgentTaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            agentTaskRepository.save(task);
            logger.info("Background task {} completed successfully", taskId);

        } catch (Exception e) {
            logger.error("Background task {} failed", taskId, e);
            task.setStatus(AgentTaskStatus.FAILED);
            task.setTaskResponse("Error: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            agentTaskRepository.save(task);
        } finally {
            if (sessionId != null) {
                browserSessionManager.closeSession(sessionId);
            }
        }
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
}
