package com.mcp.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mcp.dto.AgentActionRequest;

@Service
public class AgentStreamingService {

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

        Thread.startVirtualThread(() -> {
            try {
                streamAgentAction(projectId, action, req, req.symbolId(), req.filePath(), req.query(), req.url(), req.diff(), chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE emitter send failed", e);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data("{\"error\":\"AGENT action failed: " + ex.getMessage().replace("\"", "\\\"") + "\"}"));
                } catch (IOException ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }

    public void streamAgentAction(Long projectId, String action, AgentActionRequest req,
                                 Long symbolId, String filePath, String query, String url, String diff,
                                 java.util.function.Consumer<String> chunkConsumer) throws IOException {
        String taskType = switch (action.toLowerCase()) {
            case "explain-symbol" -> "code-analyse";
            case "explain-file" -> "code-analyse";
            case "ask" -> "code-analyse";
            case "code-review" -> "code-review";
            case "code-refactor", "code-optimise" -> "code-refactor";
            case "web-search" -> "web-search";
            case "code-commit" -> "code-commit";
            case "java-doc" -> "java-doc";
            case "junit-test-cases" -> "junit-test-cases";
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };

        if ("web-search".equalsIgnoreCase(action)) {
            String sessionId = null;
            try {
                sessionId = browserSessionManager.createSession(new com.mcp.dto.browser.BrowserSessionRequest("chromium", true, null, null, projectId));
                String q = query != null ? query : req.query();
                String u = url != null ? url : req.url();
                String pageText = webSearchOrchestrator.fetchWebSearchContent(q, u, sessionId);
                List<AgentClient.Message> messages = promptBuilder.buildWebSearchMessages(projectId, q, u, pageText);
                agentClient.streamChat(messages, taskType, chunkConsumer);
            } finally {
                if (sessionId != null) {
                    browserSessionManager.closeSession(sessionId);
                }
            }
            return;
        }

        List<AgentClient.Message> messages = switch (action.toLowerCase()) {
            case "explain-symbol" -> {
                Long sId = symbolId != null ? symbolId : req.symbolId();
                yield promptBuilder.buildExplainSymbolMessages(projectId, sId);
            }
            case "explain-file" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield promptBuilder.buildExplainFileMessages(projectId, path);
            }
            case "ask" -> promptBuilder.buildAskCodebaseMessages(projectId, req.question());
            case "code-review" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield promptBuilder.buildCodeReviewMessages(projectId, path);
            }
            case "code-refactor", "code-optimise" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield promptBuilder.buildCodeRefactorMessages(projectId, path);
            }
            case "code-commit" -> {
                String d = diff != null ? diff : req.diff();
                yield promptBuilder.buildCodeCommitMessages(projectId, d);
            }
            case "java-doc" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield promptBuilder.buildJavaDocMessages(projectId, path);
            }
            case "junit-test-cases" -> {
                String path = filePath != null ? filePath : req.filePath();
                yield promptBuilder.buildJunitTestCasesMessages(projectId, path);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };

        agentClient.streamChat(messages, taskType, chunkConsumer);
    }
}
