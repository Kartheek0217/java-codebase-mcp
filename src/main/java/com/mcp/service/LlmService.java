package com.mcp.service;

import com.mcp.dto.LlmActionRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * High-level LLM service that acts as a facade, delegating to specialized components.
 */
@Service
public class LlmService {

    private final LlmClient llmClient;
    private final LlmPromptBuilder promptBuilder;
    private final LlmStreamingService streamingService;
    private final WebSearchOrchestrator webSearchOrchestrator;
    private final BrowserSessionManager browserSessionManager;

    public LlmService(LlmClient llmClient,
                      LlmPromptBuilder promptBuilder,
                      LlmStreamingService streamingService,
                      WebSearchOrchestrator webSearchOrchestrator,
                      BrowserSessionManager browserSessionManager) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.streamingService = streamingService;
        this.webSearchOrchestrator = webSearchOrchestrator;
        this.browserSessionManager = browserSessionManager;
    }

    public String explainSymbol(Long projectId, Long symbolId) {
        return llmClient.chat(promptBuilder.buildExplainSymbolMessages(projectId, symbolId), "code-analyse");
    }

    public String explainFile(Long projectId, String filePath) throws IOException {
        return llmClient.chat(promptBuilder.buildExplainFileMessages(projectId, filePath), "code-analyse");
    }

    public String askCodebase(Long projectId, String question) {
        return llmClient.chat(promptBuilder.buildAskCodebaseMessages(projectId, question), "code-analyse");
    }

    public String codeReview(Long projectId, String filePath) throws IOException {
        return llmClient.chat(promptBuilder.buildCodeReviewMessages(projectId, filePath), "code-review");
    }

    public String codeRefactor(Long projectId, String filePath) throws IOException {
        return llmClient.chat(promptBuilder.buildCodeRefactorMessages(projectId, filePath), "code-refactor");
    }

    public String codeCommit(Long projectId, String diff) {
        return llmClient.chat(promptBuilder.buildCodeCommitMessages(projectId, diff), "code-commit");
    }

    public String javaDoc(Long projectId, String filePath) throws IOException {
        return llmClient.chat(promptBuilder.buildJavaDocMessages(projectId, filePath), "java-doc");
    }

    public String junitTestCases(Long projectId, String filePath) throws IOException {
        return llmClient.chat(promptBuilder.buildJunitTestCasesMessages(projectId, filePath), "junit-test-cases");
    }

    public String webSearchAndAnalyse(Long projectId, String query, String url) {
        String sessionId = null;
        try {
            sessionId = browserSessionManager.createSession(new com.mcp.dto.browser.BrowserSessionRequest("chromium", true, null, null, projectId));
            String pageText = webSearchOrchestrator.fetchWebSearchContent(query, url, sessionId);
            List<LlmClient.Message> messages = promptBuilder.buildWebSearchMessages(projectId, query, url, pageText);
            return llmClient.chat(messages, "web-search");
        } finally {
            if (sessionId != null) {
                browserSessionManager.closeSession(sessionId);
            }
        }
    }

    public SseEmitter streamResponse(Long projectId, String action, LlmActionRequest req) {
        return streamingService.streamResponse(projectId, action, req);
    }
}
