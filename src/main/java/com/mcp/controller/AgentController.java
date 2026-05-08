package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.ContentSearchResult;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Project;
import com.mcp.entity.Skill;
import com.mcp.entity.Symbol;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.service.FileIndexerService;
import com.mcp.service.LuceneIndexService;
import com.mcp.service.SkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "Agent", description = "Endpoints optimized for AI agents to gather codebase context and perform semantic searches.")
public class AgentController {
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final FileIndexerService fileIndexerService;
    private final FileMetadataRepository fileMetadataRepository;
    private final SymbolRepository symbolRepository;
    private final LuceneIndexService luceneIndexService;
    private final ProjectRepository projectRepository;
    private final SkillRepository skillRepository;
    private final SkillService skillService;

    // Simple history tracking in-memory
    private final ConcurrentLinkedQueue<Map<String, Object>> accessHistory = new ConcurrentLinkedQueue<>();
    private final Map<String, Map<String, Object>> sessionStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sessionCleanup = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Cleanup old sessions every hour (TTL: 1 hour)
        sessionCleanup.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            sessionStore.entrySet().removeIf(e -> (now - (Long) e.getValue().get("createdAt")) > 3600000L);
        }, 1, 1, TimeUnit.HOURS);
    }

    public AgentController(FileIndexerService fileIndexerService,
            FileMetadataRepository fileMetadataRepository,
            SymbolRepository symbolRepository,
            LuceneIndexService luceneIndexService,
            ProjectRepository projectRepository,
            SkillRepository skillRepository,
            SkillService skillService) {
        this.fileIndexerService = fileIndexerService;
        this.fileMetadataRepository = fileMetadataRepository;
        this.symbolRepository = symbolRepository;
        this.luceneIndexService = luceneIndexService;
        this.projectRepository = projectRepository;
        this.skillRepository = skillRepository;
        this.skillService = skillService;
    }

    /**
     * Retrieves the full context for a specific file, including its content,
     * indexed symbols, and metadata.
     *
     * @param projectId     The ID of the project containing the file
     * @param filePath      The relative path of the file from the project root
     * @param correlationId Optional correlation ID for tracing AI agent requests
     * @return A map containing the file content, symbols, and metadata
     * @throws IOException If the file cannot be read
     */
    @GetMapping("/context")
    @Operation(summary = "Get full context for a file", description = "Retrieves the full content of a file along with its AST-extracted symbols (classes, methods, variables) and metadata. This is the primary endpoint for an AI agent to 'read' a file with architectural awareness.", responses = {
            @ApiResponse(responseCode = "200", description = "File context retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Project or File not found")
    })
    public Map<String, Object> getContext(
            @Parameter(description = "The ID of the project containing the file") @RequestParam Long projectId,
            @Parameter(description = "The relative path of the file from the project root") @RequestParam String filePath,
            @Parameter(description = "Optional correlation ID for tracing AI agent requests") @RequestParam(required = false) String correlationId)
            throws IOException {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        Path projectRoot = Paths.get(project.getRootPath());
        Path fullPath = projectRoot.resolve(filePath).toAbsolutePath();
        String absolutePath = fullPath.toString();

        if (!Files.exists(fullPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filePath);
        }

        FileMetadata metadata = fileMetadataRepository.findById(new FileMetadataId(projectId, absolutePath))
                .orElse(null);
        List<Symbol> symbols = fileIndexerService.getSymbols(projectId, absolutePath);
        String content = Files.readString(fullPath);

        Map<String, Object> context = new HashMap<>();
        if (correlationId != null) {
            context.put("_correlationId", correlationId);
        }
        context.put("path", filePath);
        context.put("content", content);
        context.put("symbols", symbols);
        context.put("metadata", metadata);

        trackAccess(projectId, "CONTEXT", filePath);

        return context;
    }

    /**
     * Searches for symbols (classes, methods, fields) across the project based on a
     * query.
     *
     * @param projectId The ID of the project to search in
     * @param query     The search query string
     * @param type      Optional filter for symbol type (e.g., CLASS, METHOD)
     * @return A list of matching symbols
     */
    @GetMapping("/symbols")
    @Operation(summary = "AI-optimized symbol search", description = "Searches for symbols (classes, methods, fields) across the project by name or type.", responses = {
            @ApiResponse(responseCode = "200", description = "Symbols retrieved successfully")
    })
    public List<Symbol> aiSymbolSearch(
            @Parameter(description = "The ID of the project") @RequestParam Long projectId,
            @Parameter(description = "The symbol name to search for") @RequestParam String query,
            @Parameter(description = "The type of symbol (CLASS, METHOD, FIELD)") @RequestParam(required = false) String type) {

        trackAccess(projectId, "SYMBOLS", query);
        if (type != null && !type.isEmpty()) {
            try {
                com.mcp.entity.SymbolType symbolType = com.mcp.entity.SymbolType.valueOf(type.toUpperCase());
                return symbolRepository.findByProjectIdAndNameContainingIgnoreCaseAndType(projectId, query, symbolType);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid symbol type provided: {}", type);
                return symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query);
            }
        } else {
            return symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query);
        }
    }

    /**
     * Performs a semantic search for code snippets relevant to the provided query.
     *
     * @param projectId The ID of the project to search in
     * @param query     The natural language or code-based query
     * @return A list of content search results with matching snippets
     */
    @GetMapping("/suggest")
    @Operation(summary = "Suggest relevant code snippets", description = "Performs a semantic/full-text search across the project to find code snippets relevant to the provided query. Useful for AI agents to find 'where' a feature might be implemented.", responses = {
            @ApiResponse(responseCode = "200", description = "Suggestions retrieved successfully")
    })
    public List<ContentSearchResult> suggest(
            @Parameter(description = "The ID of the project to search in") @RequestParam Long projectId,
            @Parameter(description = "The natural language or code-based query") @RequestParam String query) {
        trackAccess(projectId, "SUGGEST", query);
        return luceneIndexService.searchContent(projectId, query);
    }

    /**
     * Retrieves the access history for a specific project.
     *
     * @param projectId The ID of the project
     * @return A list of recent access entries
     */
    @GetMapping("/history")
    @Operation(summary = "Get project access history", description = "Returns a list of the most recent files or queries accessed by the AI agent for this project. Helps in maintaining conversation context.", responses = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    })
    public List<Map<String, Object>> getHistory(
            @Parameter(description = "The ID of the project") @RequestParam Long projectId) {
        return accessHistory.stream()
                .filter(m -> m.get("projectId").equals(projectId))
                .limit(50)
                .toList();
    }

    /**
     * Starts a new session for multi-turn AI interactions.
     *
     * @param projectId The ID of the project for the session
     * @return A map containing the generated sessionId
     */
    @PostMapping("/session/start")
    @Operation(summary = "Start a new AI session", description = "Initializes a session for multi-turn conversations with an AI agent.", responses = {
            @ApiResponse(responseCode = "200", description = "Session started successfully"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public Map<String, Object> startSession(@RequestParam Long projectId) {
        // Validate project exists
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String sessionId = UUID.randomUUID().toString();
        Map<String, Object> session = new HashMap<>();
        session.put("projectId", projectId);
        session.put("createdAt", System.currentTimeMillis());
        session.put("history", new ArrayList<>());
        sessionStore.put(sessionId, session);
        return Map.of("sessionId", sessionId);
    }

    /**
     * Retrieves details of an existing AI session.
     *
     * @param sessionId The unique ID of the session
     * @return The session data
     */
    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Get AI session details", description = "Retrieves the current state and history of an AI session.", responses = {
            @ApiResponse(responseCode = "200", description = "Session retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public Map<String, Object> getSession(@PathVariable String sessionId) {
        Map<String, Object> session = sessionStore.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return session;
    }

    /**
     * Retrieves all learned skills for a project.
     *
     * @param projectId The ID of the project
     * @return A list of learned skills
     */
    @GetMapping("/skills")
    @Operation(summary = "Get learned skills", description = "Returns a list of all skills learned for a specific project.", responses = {
            @ApiResponse(responseCode = "200", description = "Skills retrieved successfully")
    })
    public List<Skill> getSkills(@RequestParam Long projectId) {
        return skillRepository.findByProjectId(projectId);
    }

    /**
     * Deletes all learned skills for a specific project.
     *
     * @param projectId The ID of the project
     * @return A status message indicating success
     */
    @DeleteMapping("/skills")
    @Operation(summary = "Clear all skills", description = "Deletes all learned skills for a specific project.")
    public Map<String, String> deleteSkills(@RequestParam Long projectId) {
        skillService.deleteSkillsByProject(projectId);
        return Map.of("status", "success", "message", "All skills cleared for project: " + projectId);
    }

    /**
     * Instructs the agent to learn a new skill from a provided URL.
     *
     * @param projectId The ID of the project to associate the skill with
     * @param url       The URL to the skill definition (Markdown)
     * @return A status message indicating success or failure
     */
    @PostMapping("/skills/learn")
    @Operation(summary = "Learn skill from URL", description = "Fetches a Markdown file from a URL and learns the skill defined in it.", responses = {
            @ApiResponse(responseCode = "200", description = "Skill learning triggered successfully")
    })
    public Map<String, String> learnSkill(@RequestParam Long projectId, @RequestParam String url) {
        try {
            skillService.learnFromUrl(projectId, url);
            return Map.of("status", "success", "message", "Skill learned successfully");
        } catch (Exception e) {
            logger.error("Failed to learn skill from URL: {}", url, e);
            return Map.of("status", "error", "message", "Failed to learn skill: " + e.getMessage());
        }
    }

    private void trackAccess(Long projectId, String type, String value) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("projectId", projectId);
        entry.put("type", type);
        entry.put("value", value);
        entry.put("timestamp", System.currentTimeMillis());
        accessHistory.add(entry);
        if (accessHistory.size() > 1000) {
            accessHistory.poll();
        }
    }
}
