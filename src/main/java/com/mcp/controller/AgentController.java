package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.AccessEntry;
import com.mcp.dto.CompressedContextDTO;
import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.ContextDTO;
import com.mcp.dto.FileMetadataDTO;
import com.mcp.dto.SessionDTO;
import com.mcp.dto.SymbolDTO;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Project;
import com.mcp.entity.Skill;
import com.mcp.entity.Symbol;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.service.ContextMemoryService;
import com.mcp.service.FileIndexerService;
import com.mcp.service.LuceneIndexService;
import com.mcp.service.SkillService;
import com.mcp.service.TopologyService;

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
	private final TopologyService topologyService;
	private final ContextMemoryService contextMemoryService;

	// Simple history tracking in-memory
	private final ConcurrentLinkedQueue<AccessEntry> accessHistory = new ConcurrentLinkedQueue<>();

	private static class Session {
		private final String sessionId;
		private final Long projectId;
		private final long createdAt;
		private final List<AccessEntry> history = Collections.synchronizedList(new ArrayList<>());

		public Session(String sessionId, Long projectId) {
			this.sessionId = sessionId;
			this.projectId = projectId;
			this.createdAt = System.currentTimeMillis();
		}

		public void addHistory(AccessEntry entry) {
			history.add(entry);
			if (history.size() > 100) {
				history.remove(0);
			}
		}

		public SessionDTO toDTO() {
			return new SessionDTO(sessionId, projectId, createdAt, new ArrayList<>(history));
		}

		public long getCreatedAt() {
			return createdAt;
		}
	}

	private final Map<String, Session> sessionStore = Collections
			.synchronizedMap(new LinkedHashMap<>(100, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
					return size() > 1000;
				}
			});

	private final ScheduledExecutorService sessionCleanup = Executors.newSingleThreadScheduledExecutor();

	@PostConstruct
	public void init() {
		// Cleanup old sessions every hour (TTL: 1 hour)
		sessionCleanup.scheduleWithFixedDelay(() -> {
			long now = System.currentTimeMillis();
			sessionStore.entrySet().removeIf(e -> {
				boolean expired = (now - e.getValue().getCreatedAt()) > 3600000L;
				if (expired) {
					contextMemoryService.clearSession(e.getKey());
				}
				return expired;
			});
		}, 1, 1, TimeUnit.HOURS);
	}

	public AgentController(FileIndexerService fileIndexerService, FileMetadataRepository fileMetadataRepository,
			SymbolRepository symbolRepository, LuceneIndexService luceneIndexService,
			ProjectRepository projectRepository, SkillRepository skillRepository, SkillService skillService,
			TopologyService topologyService, ContextMemoryService contextMemoryService) {
		this.fileIndexerService = fileIndexerService;
		this.fileMetadataRepository = fileMetadataRepository;
		this.symbolRepository = symbolRepository;
		this.luceneIndexService = luceneIndexService;
		this.projectRepository = projectRepository;
		this.skillRepository = skillRepository;
		this.skillService = skillService;
		this.topologyService = topologyService;
		this.contextMemoryService = contextMemoryService;
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
	@Operation(summary = "Get optimized context for a file", description = "Retrieves the content of a file along with its AST-extracted symbols and metadata. Supports truncation and filtering to save tokens.", responses = {
			@ApiResponse(responseCode = "200", description = "File context retrieved successfully"),
			@ApiResponse(responseCode = "404", description = "Project or File not found") })
	public ResponseEntity<Object> getContext(
			@Parameter(description = "The ID of the project containing the file") @RequestParam Long projectId,
			@Parameter(description = "The relative path of the file from the project root") @RequestParam String filePath,
			@Parameter(description = "Optional correlation ID for tracing AI agent requests") @RequestParam(required = false) String correlationId,
			@Parameter(description = "Optional session ID to track history") @RequestParam(required = false) String sessionId,
			@Parameter(description = "If true, returns symbols only, skipping content and metadata") @RequestParam(required = false, defaultValue = "false") boolean symbolsOnly,
			@Parameter(description = "Maximum number of lines of content to return") @RequestParam(required = false) Integer maxLines,
			@Parameter(description = "Specific line range to return (e.g., '10-50')") @RequestParam(required = false) String lineRange,
			@Parameter(description = "Output format: full, compact, minimal") @RequestParam(required = false, defaultValue = "full") String format,
			@Parameter(description = "Comma-separated list of fields to include (path, content, symbols, metadata, alreadyViewed, hasChanged)") @RequestParam(required = false) String fields,
			@Parameter(description = "Page number for pagination (1-indexed)") @RequestParam(required = false) Integer page,
			@Parameter(description = "Number of lines per page") @RequestParam(required = false) Integer pageSize,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {

		Object result = getContextInternal(projectId, filePath, correlationId, sessionId, symbolsOnly, maxLines,
				lineRange, format, fields, page, pageSize);

		String etag = null;
		if (result instanceof ContextDTO dto && dto.metadata() != null) {
			etag = dto.metadata().checksum();
		}

		if (ifNoneMatch != null && etag != null && ifNoneMatch.equals("\"" + etag + "\"")) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}

		return ResponseEntity.ok().eTag(etag).body(result);
	}

	private Object getContextInternal(Long projectId, String filePath, String correlationId, String sessionId,
			boolean symbolsOnly, Integer maxLines, String lineRange, String format, String fields, Integer page,
			Integer pageSize) throws IOException {

		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

		Path projectRoot = Paths.get(project.getRootPath());
		Path fullPath = projectRoot.resolve(filePath).toAbsolutePath();
		String absolutePath = fullPath.toString();

		if (page != null && pageSize != null) {
			int startLine = (page - 1) * pageSize + 1;
			int endLine = page * pageSize;
			lineRange = startLine + "-" + endLine;
		}

		if (!Files.exists(fullPath)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filePath);
		}

		FileMetadata metadata = symbolsOnly ? null
				: fileMetadataRepository.findById(new FileMetadataId(projectId, absolutePath)).orElse(null);

		List<Symbol> symbols = fileIndexerService.getSymbols(projectId, absolutePath);
		String content = symbolsOnly ? null : Files.readString(fullPath);

		boolean alreadyViewed = sessionId != null && contextMemoryService.hasBeenViewed(sessionId, filePath);
		String lastChecksum = sessionId != null ? contextMemoryService.getLastChecksum(sessionId, filePath) : null;
		String currentChecksum = metadata != null ? metadata.getChecksum() : null;
		boolean hasChanged = lastChecksum != null && !lastChecksum.equals(currentChecksum);

		if (content != null) {
			content = com.mcp.util.CodeUtils.truncateAndAddLineNumbers(content, maxLines, lineRange);
		}

		List<SymbolDTO> symbolDTOs = symbols.stream().map(this::toSymbolDTO).collect(Collectors.toList());

		FileMetadataDTO metadataDTO = toMetadataDTO(metadata);

		if ("minimal".equalsIgnoreCase(format)) {
			metadataDTO = null;
		}

		trackAccess(projectId, "CONTEXT", filePath, sessionId);
		contextMemoryService.recordAccess(sessionId, filePath, currentChecksum);

		ContextDTO contextDTO = new ContextDTO(filePath, content, symbolDTOs, metadataDTO, correlationId, alreadyViewed,
				hasChanged);

		if (fields != null && !fields.isEmpty()) {
			contextDTO = filterFields(contextDTO, fields);
		}

		if ("compressed".equalsIgnoreCase(format)) {
			return toCompressedDTO(contextDTO);
		}

		return contextDTO;
	}

	/**
	 * Retrieves context for multiple files in a single request.
	 *
	 * @param projectId The ID of the project
	 * @param filePaths List of relative file paths
	 * @param sessionId Optional session ID
	 * @param format    Output format (full, compact, minimal, compressed)
	 * @return A list of context objects
	 */
	@PostMapping("/context/batch")
	@Operation(summary = "Batch fetch context", description = "Retrieves context for multiple files in a single request to reduce round-trip overhead.")
	public List<Object> getBatchContext(@RequestParam Long projectId,
			@org.springframework.web.bind.annotation.RequestBody List<String> filePaths,
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false, defaultValue = "full") String format) throws IOException {
		List<Object> results = new ArrayList<>();
		for (String filePath : filePaths) {
			try {
				results.add(getContextInternal(projectId, filePath, null, sessionId, false, null, null, format, null,
						null, null));
			} catch (Exception e) {
				logger.warn("Failed to fetch context for file {} in batch: {}", filePath, e.getMessage());
			}
		}
		return results;
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
	@Operation(summary = "AI-optimized symbol search", description = "Searches for symbols (classes, methods, fields) across the project by name or type. Returns lightweight DTOs.", responses = {
			@ApiResponse(responseCode = "200", description = "Symbols retrieved successfully") })
	public List<SymbolDTO> aiSymbolSearch(
			@Parameter(description = "The ID of the project") @RequestParam Long projectId,
			@Parameter(description = "The symbol name to search for") @RequestParam String query,
			@Parameter(description = "The type of symbol (CLASS, METHOD, FIELD)") @RequestParam(required = false) String type,
			@Parameter(description = "Optional session ID to track history") @RequestParam(required = false) String sessionId,
			@Parameter(description = "Maximum number of symbols to return") @RequestParam(required = false, defaultValue = "50") int limit) {

		trackAccess(projectId, "SYMBOLS", query, sessionId);
		List<Symbol> symbols;
		if (type != null && !type.isEmpty()) {
			try {
				com.mcp.entity.SymbolType symbolType = com.mcp.entity.SymbolType.valueOf(type.toUpperCase());
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCaseAndType(projectId, query,
						symbolType);
			} catch (IllegalArgumentException e) {
				logger.warn("Invalid symbol type provided: {}", type);
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query);
			}
		} else {
			symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query);
		}

		return symbols.stream().limit(limit).map(this::toSymbolDTO).toList();
	}

	/**
	 * Retrieves the project topology, including dependencies and entry points.
	 *
	 * @param projectId The ID of the project
	 * @return A map containing the project topology
	 */
	@GetMapping("/topology")
	@Operation(summary = "Get project topology", description = "Returns a high-level overview of the project structure, including file dependencies, entry points, and top symbols.", responses = {
			@ApiResponse(responseCode = "200", description = "Topology retrieved successfully") })
	public Map<String, Object> getTopology(@RequestParam Long projectId) {
		trackAccess(projectId, "TOPOLOGY", "PROJECT_" + projectId, null);
		return topologyService.getProjectTopology(projectId);
	}

	/**
	 * Returns a compressed overview of a file.
	 *
	 * @param projectId The ID of the project
	 * @param filePath  The relative path of the file
	 * @return A summarized context DTO
	 */
	@GetMapping("/summarize")
	@Operation(summary = "Summarize a file", description = "Returns a compressed overview of a file (first 20 lines + symbol list + metadata preview).", responses = {
			@ApiResponse(responseCode = "200", description = "Summary retrieved successfully") })
	public Object summarize(@RequestParam Long projectId, @RequestParam String filePath) throws IOException {
		return getContextInternal(projectId, filePath, null, null, false, 20, null, "compact", null, null, null);
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
			@ApiResponse(responseCode = "200", description = "Suggestions retrieved successfully") })
	public List<ContentSearchResult> suggest(
			@Parameter(description = "The ID of the project to search in") @RequestParam Long projectId,
			@Parameter(description = "The natural language or code-based query") @RequestParam String query,
			@Parameter(description = "Maximum number of results to return") @RequestParam(required = false, defaultValue = "10") int limit) {
		trackAccess(projectId, "SUGGEST", query, null);
		return luceneIndexService.searchContent(projectId, query, limit);
	}

	/**
	 * Performs a compact search returning only file paths and scores.
	 *
	 * @param projectId The ID of the project
	 * @param query     The search query
	 * @param limit     Maximum number of results
	 * @return A list of maps containing path and score
	 */
	@GetMapping("/suggest-compact")
	@Operation(summary = "Compact code suggestion", description = "Returns only file paths and relevance scores without code snippets to save tokens.")
	public List<Map<String, Object>> suggestCompact(@RequestParam Long projectId, @RequestParam String query,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		trackAccess(projectId, "SUGGEST_COMPACT", query, null);
		List<ContentSearchResult> fullResults = luceneIndexService.searchContent(projectId, query, limit);
		return fullResults.stream().map(r -> Map.of("path", r.filePath(), "score", (Object) r.score()))
				.collect(Collectors.toList());
	}

	/**
	 * Retrieves the access history for a specific project.
	 *
	 * @param projectId The ID of the project
	 * @return A list of recent access entries
	 */
	@GetMapping("/history")
	@Operation(summary = "Get project access history", description = "Returns a list of the most recent files or queries accessed by the AI agent for this project. Helps in maintaining conversation context.", responses = {
			@ApiResponse(responseCode = "200", description = "History retrieved successfully") })
	public List<AccessEntry> getHistory(
			@Parameter(description = "The ID of the project") @RequestParam Long projectId) {
		return accessHistory.stream().filter(m -> m.projectId().equals(projectId)).limit(50).toList();
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
			@ApiResponse(responseCode = "404", description = "Project not found") })
	public Map<String, String> startSession(@RequestParam Long projectId) {
		// Validate project exists
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

		String sessionId = UUID.randomUUID().toString();
		sessionStore.put(sessionId, new Session(sessionId, projectId));
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
			@ApiResponse(responseCode = "404", description = "Session not found") })
	public SessionDTO getSession(@PathVariable String sessionId) {
		Session session = sessionStore.get(sessionId);
		if (session == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
		}
		return session.toDTO();
	}

	/**
	 * Retrieves all learned skills for a project.
	 *
	 * @param projectId The ID of the project
	 * @return A list of learned skills
	 */
	@GetMapping("/skills")
	@Operation(summary = "Get learned skills", description = "Returns a list of all skills learned for a specific project.", responses = {
			@ApiResponse(responseCode = "200", description = "Skills retrieved successfully") })
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
			@ApiResponse(responseCode = "200", description = "Skill learning triggered successfully") })
	public Map<String, String> learnSkill(@RequestParam Long projectId, @RequestParam String url) {
		try {
			skillService.learnFromUrl(projectId, url);
			return Map.of("status", "success", "message", "Skill learned successfully");
		} catch (Exception e) {
			logger.error("Failed to learn skill from URL: {}", url, e);
			return Map.of("status", "error", "message", "Failed to learn skill: " + e.getMessage());
		}
	}

	/**
	 * Instructs the agent to learn a new skill from a local file.
	 *
	 * @param projectId The ID of the project to associate the skill with
	 * @param filePath  The relative path to the skill definition file (Markdown)
	 * @return A status message indicating success or failure
	 */
	@PostMapping("/skills/learn-from-file")
	@Operation(summary = "Learn skill from local file", description = "Reads a Markdown file from a local path and learns the skill defined in it.", responses = {
			@ApiResponse(responseCode = "200", description = "Skill learning triggered successfully"),
			@ApiResponse(responseCode = "404", description = "File or Project not found"),
			@ApiResponse(responseCode = "403", description = "Access denied") })
	public Map<String, String> learnSkillFromFile(
			@Parameter(description = "The ID of the project") @RequestParam Long projectId,
			@Parameter(description = "The relative path to the file") @RequestParam String filePath) {
		try {
			skillService.learnFromFile(projectId, filePath);
			return Map.of("status", "success", "message", "Skill learned successfully from file: " + filePath);
		} catch (SecurityException e) {
			logger.warn("Security violation while learning skill from file: {}", filePath);
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
		} catch (IOException e) {
			logger.error("Failed to read skill file: {}", filePath, e);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (Exception e) {
			logger.error("Failed to learn skill from file: {}", filePath, e);
			return Map.of("status", "error", "message", "Failed to learn skill: " + e.getMessage());
		}
	}

	private CompressedContextDTO toCompressedDTO(ContextDTO dto) {
		List<CompressedContextDTO.CompressedSymbolDTO> symbols = dto.symbols() == null ? null
				: dto.symbols().stream()
						.map(s -> new CompressedContextDTO.CompressedSymbolDTO(s.name(), s.type().name()))
						.toList();

		int lines = dto.content() == null ? 0 : dto.content().split("\\r?\\n").length;

		return new CompressedContextDTO(dto.path(), dto.content(), symbols, lines);
	}

	private SymbolDTO toSymbolDTO(Symbol symbol) {
		return new SymbolDTO(symbol.getName(), symbol.getType(), symbol.getFilePath());
	}

	private ContextDTO filterFields(ContextDTO dto, String fieldsStr) {
		Set<String> fieldSet = Arrays.stream(fieldsStr.split(",")).map(String::trim).map(String::toLowerCase)
				.collect(Collectors.toSet());

		return new ContextDTO(fieldSet.contains("path") ? dto.path() : null,
				fieldSet.contains("content") ? dto.content() : null,
				fieldSet.contains("symbols") ? dto.symbols() : null,
				fieldSet.contains("metadata") ? dto.metadata() : null, dto._correlationId(), // Always include
																								// correlation ID if
																								// present
				fieldSet.contains("alreadyviewed") ? dto.alreadyViewed() : null,
				fieldSet.contains("haschanged") ? dto.hasChanged() : null);
	}

	private FileMetadataDTO toMetadataDTO(FileMetadata metadata) {
		if (metadata == null)
			return null;
		return new FileMetadataDTO(metadata.getFilePath(), metadata.getFileSize(), metadata.getChecksum());
	}

	private void trackAccess(Long projectId, String type, String value, String sessionId) {
		AccessEntry entry = new AccessEntry(projectId, type, value, System.currentTimeMillis());
		accessHistory.add(entry);
		if (accessHistory.size() > 1000) {
			accessHistory.poll();
		}

		if (sessionId != null) {
			Session session = sessionStore.get(sessionId);
			if (session != null) {
				session.addHistory(entry);
			}
		}
	}
}
