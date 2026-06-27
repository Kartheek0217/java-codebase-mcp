package com.mcp.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mcp.dto.AgentActionRequest;
import java.util.function.Consumer;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentStreamingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentClient agentClient;
    private final AgentPromptBuilder promptBuilder;
    private final BrowserSessionManager browserSessionManager;
    private final WebSearchOrchestrator webSearchOrchestrator;

    public AgentStreamingService(AgentClient agentClient,
                               AgentPromptBuilder promptBuilder,
                               BrowserSessionManager browserSessionManager,
                               WebSearchOrchestrator webSearchOrchestrator) {
        this.agentClient = agentClient;
        this.promptBuilder = promptBuilder;
        this.browserSessionManager = browserSessionManager;
        this.webSearchOrchestrator = webSearchOrchestrator;
    }

    public SseEmitter streamResponse(Long projectId, String action, AgentActionRequest req) {
        SseEmitter emitter = new SseEmitter(180000L); // 3-minute timeout
        AtomicBoolean completed = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            if (completed.compareAndSet(false, true)) {
                emitter.complete();
            }
        });
        emitter.onError(e -> {
            if (completed.compareAndSet(false, true)) {
                emitter.completeWithError(e);
            }
        });

        Thread.startVirtualThread(() -> {
            try {
                streamAgentAction(projectId, action, req, req.symbolId(), req.filePath(), req.query(), req.url(), req.diff(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE emitter send failed", e);
                    }
                });
            } catch (Exception ex) {
                try {
                    String safeMsg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                    String json = OBJECT_MAPPER.writeValueAsString(Map.of("error", "AGENT action failed: " + safeMsg));
                    emitter.send(SseEmitter.event().name("error").data(json));
                } catch (IOException ignored) {}
                if (completed.compareAndSet(false, true)) {
                    emitter.completeWithError(ex);
                }
            } finally {
                if (completed.compareAndSet(false, true)) {
                    emitter.complete();
                }
            }
        });

        return emitter;
    }

    public void streamAgentAction(Long projectId, String action, AgentActionRequest req,
                                 Long symbolId, String filePath, String query, String url, String diff,
                                 Consumer<String> chunkConsumer) throws IOException {
        String taskType;
        List<AgentClient.Message> messages;

        switch (action.toLowerCase()) {
            case "explain-symbol" -> {
                taskType = "code-analyse";
                Long sId = symbolId != null ? symbolId : req.symbolId();
                messages = promptBuilder.buildExplainSymbolMessages(projectId, sId);
            }
            case "explain-file" -> {
                taskType = "code-analyse";
                String path = filePath != null ? filePath : req.filePath();
                messages = promptBuilder.buildExplainFileMessages(projectId, path);
            }
            case "ask" -> {
                taskType = "code-analyse";
                messages = promptBuilder.buildAskCodebaseMessages(projectId, req.question());
            }
            case "code-review" -> {
                taskType = "code-review";
                String path = filePath != null ? filePath : req.filePath();
                messages = promptBuilder.buildCodeReviewMessages(projectId, path);
            }
            case "code-refactor", "code-optimise" -> {
                taskType = "code-refactor";
                String path = filePath != null ? filePath : req.filePath();
                messages = promptBuilder.buildCodeRefactorMessages(projectId, path);
            }
            case "web-search" -> {
                taskType = "web-search";
                String sessionId = null;
                try {
                    sessionId = browserSessionManager.createSession(new com.mcp.dto.browser.BrowserSessionRequest("chromium", true, null, null, projectId));
                    String q = query != null ? query : req.query();
                    String u = url != null ? url : req.url();
                    String pageText = webSearchOrchestrator.fetchWebSearchContent(q, u, sessionId);
                    messages = promptBuilder.buildWebSearchMessages(projectId, q, u, pageText);
                } finally {
                    if (sessionId != null) {
                        browserSessionManager.closeSession(sessionId);
                    }
                }
            }
            case "code-commit" -> {
                taskType = "code-commit";
                String d = diff != null ? diff : req.diff();
                messages = promptBuilder.buildCodeCommitMessages(projectId, d);
            }
            case "java-doc" -> {
                taskType = "java-doc";
                String path = filePath != null ? filePath : req.filePath();
                messages = promptBuilder.buildJavaDocMessages(projectId, path);
            }
            case "junit-test-cases" -> {
                taskType = "junit-test-cases";
                String path = filePath != null ? filePath : req.filePath();
                messages = promptBuilder.buildJunitTestCasesMessages(projectId, path);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }

        agentClient.streamChat(messages, taskType, chunkConsumer);
    }
}
