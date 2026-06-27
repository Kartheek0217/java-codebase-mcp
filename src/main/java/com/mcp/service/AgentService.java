package com.mcp.service;

import com.mcp.dto.AgentActionRequest;
import com.mcp.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
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
    private final ProjectRepository projectRepository;

    public AgentService(AgentClient agentClient,
                      AgentPromptBuilder promptBuilder,
                      AgentStreamingService streamingService,
                      WebSearchOrchestrator webSearchOrchestrator,
                      BrowserSessionManager browserSessionManager,
                      ProjectRepository projectRepository) {
        this.agentClient = agentClient;
        this.promptBuilder = promptBuilder;
        this.streamingService = streamingService;
        this.webSearchOrchestrator = webSearchOrchestrator;
        this.browserSessionManager = browserSessionManager;
        this.projectRepository = projectRepository;
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

    private void validateFilePath(Long projectId, String filePath) {
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId))
                .getRootPath();
        com.mcp.util.PathSecurityUtil.validateAndNormalizePath(rootPath, filePath);
    }

    public AgentActionRequest validateAndMergeParameters(Long projectId, String action, Long symbolId, String filePath,
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
}
