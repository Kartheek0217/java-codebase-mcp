package com.mcp.service;

import com.mcp.dto.AgentActionRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mcp.dto.browser.BrowserSessionRequest;

import java.io.IOException;
import java.util.List;

/**
 * High-level AGENT service that acts as a facade, delegating to specialized components.
 */
@Service
public class AgentService {

    private final AgentClient agentClient;
    private final AgentPromptBuilder promptBuilder;
    private final AgentStreamingService streamingService;
    private final WebSearchOrchestrator webSearchOrchestrator;
    private final BrowserSessionManager browserSessionManager;

    public AgentService(AgentClient agentClient,
                      AgentPromptBuilder promptBuilder,
                      AgentStreamingService streamingService,
                      WebSearchOrchestrator webSearchOrchestrator,
                      BrowserSessionManager browserSessionManager) {
        this.agentClient = agentClient;
        this.promptBuilder = promptBuilder;
        this.streamingService = streamingService;
        this.webSearchOrchestrator = webSearchOrchestrator;
        this.browserSessionManager = browserSessionManager;
    }

    public String explainSymbol(Long projectId, Long symbolId) {
        return agentClient.chat(promptBuilder.buildExplainSymbolMessages(projectId, symbolId), "code-analyse");
    }

    public String explainFile(Long projectId, String filePath) throws IOException {
        return agentClient.chat(promptBuilder.buildExplainFileMessages(projectId, filePath), "code-analyse");
    }

    public String askCodebase(Long projectId, String question) {
        return agentClient.chat(promptBuilder.buildAskCodebaseMessages(projectId, question), "code-analyse");
    }

    public String codeReview(Long projectId, String filePath) throws IOException {
        return agentClient.chat(promptBuilder.buildCodeReviewMessages(projectId, filePath), "code-review");
    }

    public String codeRefactor(Long projectId, String filePath) throws IOException {
        return agentClient.chat(promptBuilder.buildCodeRefactorMessages(projectId, filePath), "code-refactor");
    }

    public String codeCommit(Long projectId, String diff) {
        return agentClient.chat(promptBuilder.buildCodeCommitMessages(projectId, diff), "code-commit");
    }

    public String javaDoc(Long projectId, String filePath) throws IOException {
        return agentClient.chat(promptBuilder.buildJavaDocMessages(projectId, filePath), "java-doc");
    }

    public String junitTestCases(Long projectId, String filePath) throws IOException {
        return agentClient.chat(promptBuilder.buildJunitTestCasesMessages(projectId, filePath), "junit-test-cases");
    }

    public String webSearchAndAnalyse(Long projectId, String query, String url) {
        String sessionId = null;
        try {
            sessionId = browserSessionManager.createSession(new BrowserSessionRequest("chromium", true, null, null, projectId));
            String pageText = webSearchOrchestrator.fetchWebSearchContent(query, url, sessionId);
            List<AgentClient.Message> messages = promptBuilder.buildWebSearchMessages(projectId, query, url, pageText);
            return agentClient.chat(messages, "web-search");
        } finally {
            if (sessionId != null) {
                browserSessionManager.closeSession(sessionId);
            }
        }
    }

    public SseEmitter streamResponse(Long projectId, String action, AgentActionRequest req) {
        return streamingService.streamResponse(projectId, action, req);
    }

    public String syncResponse(Long projectId, String action, AgentActionRequest req) throws IOException {
        return switch (action.toLowerCase()) {
            case "explain-symbol" -> explainSymbol(projectId, req.symbolId());
            case "explain-file" -> explainFile(projectId, req.filePath());
            case "ask" -> askCodebase(projectId, req.question());
            case "code-review" -> codeReview(projectId, req.filePath());
            case "code-refactor", "code-optimise" -> codeRefactor(projectId, req.filePath());
            case "web-search" -> webSearchAndAnalyse(projectId, req.query(), req.url());
            case "code-commit" -> codeCommit(projectId, req.diff());
            case "java-doc" -> javaDoc(projectId, req.filePath());
            case "junit-test-cases" -> junitTestCases(projectId, req.filePath());
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }
}
