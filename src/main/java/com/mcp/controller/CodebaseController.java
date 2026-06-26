package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;


import com.mcp.dto.ContextDTO;
import com.mcp.dto.SearchOptions;
import com.mcp.entity.Project;
import com.mcp.entity.Symbol;
import com.mcp.properties.CodebaseProperties;
import com.mcp.service.CodeSummarizerService;
import com.mcp.service.CodebaseQueryFacade;
import com.mcp.service.ContextMemoryService;
import com.mcp.service.EndpointAnalysisService;
import com.mcp.service.FileIndexerService;
import com.mcp.service.FileScannerService;
import com.mcp.service.GitInfoService;
import com.mcp.service.LuceneIndexService;
import com.mcp.service.ProjectService;
import com.mcp.service.ReconciliationService;
import com.mcp.service.TopologyService;
import com.mcp.util.CodeUtils;
import com.mcp.util.AgentResponseOptimizer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Unified codebase read / mutation controller.
 *
 * <p>
 * Direct repository access has been removed — all symbol/file queries are
 * delegated to {@link CodebaseQueryFacade}, keeping this controller focused on
 * HTTP concerns and operation routing.
 */
@RestController
@RequestMapping("/api/codebase")
@Tag(name = "Codebase", description = "Unified endpoints for codebase analysis, file reading, search, and index management.")
public class CodebaseController {

	private final FileIndexerService fileIndexerService;
	private final LuceneIndexService luceneIndexService;
	private final ProjectService projectService;
	private final TopologyService topologyService;
	private final ContextMemoryService contextMemoryService;
	private final FileScannerService fileScannerService;
	private final ReconciliationService reconciliationService;
	private final GitInfoService gitInfoService;
	private final CodeSummarizerService codeSummarizerService;
	private final EndpointAnalysisService endpointAnalysisService;
	private final CodebaseProperties codebaseProperties;
	private final CodebaseQueryFacade codebaseQueryFacade;
	private final ObjectMapper objectMapper;

	public CodebaseController(FileIndexerService fileIndexerService,
			LuceneIndexService luceneIndexService,
			ProjectService projectService,
			TopologyService topologyService,
			ContextMemoryService contextMemoryService,
			FileScannerService fileScannerService,
			ReconciliationService reconciliationService,
			GitInfoService gitInfoService,
			CodeSummarizerService codeSummarizerService,
			EndpointAnalysisService endpointAnalysisService,
			CodebaseProperties codebaseProperties,
			CodebaseQueryFacade codebaseQueryFacade,
			ObjectMapper objectMapper) {
		this.fileIndexerService = fileIndexerService;
		this.luceneIndexService = luceneIndexService;
		this.projectService = projectService;
		this.topologyService = topologyService;
		this.contextMemoryService = contextMemoryService;
		this.fileScannerService = fileScannerService;
		this.reconciliationService = reconciliationService;
		this.gitInfoService = gitInfoService;
		this.codeSummarizerService = codeSummarizerService;
		this.endpointAnalysisService = endpointAnalysisService;
		this.codebaseProperties = codebaseProperties;
		this.codebaseQueryFacade = codebaseQueryFacade;
		this.objectMapper = objectMapper;
	}


	// ─── Project-scoped read (GET) ────────────────────────────────────────────

	@GetMapping("/{projectId}/file")
	@Operation(summary = "read_file", description = "Read a single file with its symbols and metadata.")
	public Object readFile(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Relative path to the file") @RequestParam String filePath,
			@Parameter(description = "Format (full|structure|summary|numbered|markdown)") @RequestParam(required = false, defaultValue = "full") String format,
			@Parameter(description = "Session ID") @RequestParam(required = false) String sessionId,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {
		return getFileContext(projectId, filePath, sessionId, format, ifNoneMatch);
	}

	@GetMapping("/{projectId}/search")
	@Operation(summary = "search_content", description = "Full-text Lucene search across all indexed files.")
	public Object searchContent(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Search query") @RequestParam String query,
			@Parameter(description = "Result limit") @RequestParam(required = false, defaultValue = "10") Integer limit) throws IOException {
		requireParam(query, "query");
		return luceneIndexService.searchContent(projectId,
				SearchOptions.builder().query(query).limit(limit).build());
	}

	@GetMapping("/{projectId}/search-changed")
	@Operation(summary = "search_changed_content", description = "Full-text search restricted to uncommitted (modified/added/staged) files only.")
	public Object searchChangedContent(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Search query") @RequestParam String query,
			@Parameter(description = "Result limit") @RequestParam(required = false, defaultValue = "10") Integer limit) throws IOException {
		requireParam(query, "query");
		Project project = projectService.getProject(projectId);
		Set<String> changed = gitInfoService.getChangedFilePaths(projectId);
		Set<String> absPaths = changed.stream()
				.map(rel -> Paths.get(project.getRootPath()).resolve(rel).toAbsolutePath().toString())
				.collect(Collectors.toSet());
		return luceneIndexService.searchContent(projectId,
				SearchOptions.builder().query(query).filePaths(absPaths).limit(limit).build());
	}

	@GetMapping("/{projectId}/symbols")
	@Operation(summary = "search_symbols", description = "Search for classes, methods, constructors, or fields by name.")
	public Object searchSymbols(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Search query") @RequestParam String query,
			@Parameter(description = "Symbol type (CLASS|METHOD|FIELD|CONSTRUCTOR)") @RequestParam(required = false) String type,
			@Parameter(description = "Result limit") @RequestParam(required = false, defaultValue = "50") Integer limit) {
		requireParam(query, "query");
		return codebaseQueryFacade.searchSymbols(projectId, query, type, limit);
	}

	@GetMapping("/{projectId}/files")
	@Operation(summary = "find_files", description = "Find indexed files whose paths contain the query string.")
	public Object findFiles(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Search query") @RequestParam String query,
			@Parameter(description = "Result limit") @RequestParam(required = false, defaultValue = "100") Integer limit) {
		requireParam(query, "query");
		return codebaseQueryFacade.searchFiles(projectId, query, limit);
	}

	@GetMapping("/{projectId}/suggest")
	@Operation(summary = "suggest_context", description = "Combined symbol + content search for relevant code context. Returns top-10 symbols and top-10 content hits.")
	public Object suggestContext(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Search query") @RequestParam String query) throws IOException {
		requireParam(query, "query");
		return codebaseQueryFacade.suggest(projectId, query);
	}

	@GetMapping("/{projectId}/history")
	@Operation(summary = "get_session_history", description = "Return file paths accessed in a session.")
	public Object getSessionHistory(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Session ID") @RequestParam String sessionId) {
		requireParam(sessionId, "sessionId");
		return contextMemoryService.getSessionFiles(sessionId);
	}

	@GetMapping("/{projectId}/topology")
	@Operation(summary = "get_project_topology", description = "Return project package structure and dependency graph.")
	public Object getProjectTopology(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId) {
		return topologyService.getProjectTopology(projectId);
	}

	@GetMapping("/{projectId}/summarize")
	@Operation(summary = "summarize_file", description = "Generate an AI summary of a file.")
	public Object summarizeFile(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Relative file path") @RequestParam String filePath) throws IOException {
		requireParam(filePath, "filePath");
		Project project = projectService.getProject(projectId);
		Path full = Paths.get(project.getRootPath()).resolve(filePath);
		String content = Files.readString(full);
		String summary = codeSummarizerService.createIntelligentSummary(content);
		List<Symbol> symbols = fileIndexerService.getSymbols(projectId, full.toString());
		return Map.of(
				"filePath", filePath,
				"summary", summary,
				"symbols", symbols.stream().map(s -> codebaseQueryFacade.toSymbolDTO(s, null)).toList());
	}

	@GetMapping("/{projectId}/analyze-endpoint")
	@Operation(summary = "analyze_endpoint", description = "Trace a controller endpoint down to entity level.")
	public Object analyzeEndpoint(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@Parameter(description = "Controller name") @RequestParam String controllerName,
			@Parameter(description = "Method name") @RequestParam String methodName) throws IOException {
		requireParam(controllerName, "controllerName");
		requireParam(methodName, "methodName");
		return endpointAnalysisService.analyzeEndpoint(projectId, controllerName, methodName);
	}

	// ─── Project-scoped mutations (POST) ─────────────────────────────────────

	@PostMapping("/{projectId}/scan")
	@Operation(summary = "scan_project", description = "Trigger a directory scan to detect new/changed/deleted files.")
	public Object scanProject(@Parameter(description = "Numeric Project ID") @PathVariable Long projectId) {
		fileScannerService.scanProject(projectId);
		return projectService.buildProjectOpResponse(projectId, "scan", null);
	}

	@PostMapping("/{projectId}/reconcile")
	@Operation(summary = "reconcile_index", description = "Reconcile the symbol index against the current filesystem state.")
	public Object reconcileIndex(@Parameter(description = "Numeric Project ID") @PathVariable Long projectId) {
		reconciliationService.reconcileProject(projectId);
		return projectService.buildProjectOpResponse(projectId, "reconcile", null);
	}

	@PostMapping("/{projectId}/batch")
	@Operation(summary = "batch_read_files", description = "Fetch content for multiple files in parallel.")
	public Object batchReadFiles(
			@Parameter(description = "Numeric Project ID") @PathVariable Long projectId,
			@RequestBody String rawPayload) throws IOException {
		List<String> filePaths;
		try {
			JsonNode rawInput = objectMapper.readTree(rawPayload);
			if (rawInput.isArray()) {
				filePaths = objectMapper.readerForListOf(String.class).readValue(rawInput);
			} else if (rawInput.isObject() && rawInput.has("body")) {
				JsonNode bodyNode = rawInput.get("body");
				if (bodyNode.isArray()) {
					filePaths = objectMapper.readerForListOf(String.class).readValue(bodyNode);
				} else {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body property must be an array");
				}
			} else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid batch request format: expected array or object with body array");
			}
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse batch request: " + e.getMessage(), e);
		}

		if (filePaths == null || filePaths.isEmpty())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must be a non-empty list of file paths");
		return codebaseQueryFacade.getBatchContext(projectId, filePaths,
				(pid, fp) -> {
					try {
						Object response = getFileContext(pid, fp, null, "full", null);
						return response instanceof ResponseEntity<?> re ? re.getBody() : response;
					} catch(IOException e) {
						throw new RuntimeException(e);
					}
				});
	}

	// ─── Symbol sub-resource ─────────────────────────────────────────────────

	@GetMapping("/symbols/{id}")
	@Operation(summary = "get_symbol_detail", description = "Retrieve full Symbol entity by ID.")
	public Object getSymbolDetail(@PathVariable Long id) {
		return codebaseQueryFacade.getSymbolById(id);
	}

	@GetMapping("/symbols/{id}/hierarchy")
	@Operation(summary = "get_call_hierarchy", description = "Retrieve call hierarchy for the symbol.")
	public Object getCallHierarchy(@PathVariable Long id) {
		Symbol symbol = codebaseQueryFacade.getSymbolById(id);
		return codebaseQueryFacade.buildHierarchy(symbol);
	}

	private ResponseEntity<Object> getFileContext(Long projectId, String filePath, String sessionId, String format,
			String ifNoneMatch) throws IOException {
		requireParam(filePath, "filePath");

		Project project = projectService.getProject(projectId);

		Path fullPath = com.mcp.util.PathSecurityUtil.validateAndNormalizePath(project.getRootPath(), filePath);

		if (!Files.exists(fullPath))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filePath);

		// Auto-downgrade to structure for large files to prevent massive token payloads
		long fileBytes = Files.size(fullPath);
		long maxBytes = (long) codebaseProperties.getMaxFileSizeKb() * 1024;
		if (fileBytes > maxBytes && "full".equalsIgnoreCase(format))
			format = "structure";

		List<Symbol> symbols = fileIndexerService.getSymbols(projectId, fullPath.toString());
		String content = Files.readString(fullPath);
		String finalContent;
		String summary = null;

		if ("structure".equalsIgnoreCase(format)) {
			String structureContent = codeSummarizerService.extractStructure(content);
			finalContent = CodeUtils.stripJavaImports(structureContent);
		} else if ("summary".equalsIgnoreCase(format)) {
			summary = codeSummarizerService.createIntelligentSummary(content);
			finalContent = null;
		} else if ("numbered".equalsIgnoreCase(format)) {
			finalContent = CodeUtils.addLineNumbers(content);
		} else {
			finalContent = content;
		}

		// Retrieve exact metadata DTO via composite key lookup
		var metaDTO = codebaseQueryFacade.getFileMetadata(projectId, fullPath.toString());
		String currentChecksum = metaDTO != null ? metaDTO.checksum() : null;

		if (ifNoneMatch != null && currentChecksum != null && ifNoneMatch.equals("\"" + currentChecksum + "\""))
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();

		ContextDTO contextDTO = new ContextDTO(filePath, finalContent, summary, format,
				symbols.stream().map(s -> codebaseQueryFacade.toSymbolDTO(s, null)).toList(), metaDTO, null, false,
				false);

		if ("markdown".equalsIgnoreCase(format))
			return ResponseEntity.ok().eTag(currentChecksum)
					.body(Map.of("type", "markdown", "content", AgentResponseOptimizer.toMarkdown(contextDTO)));

		if (sessionId != null)
			contextMemoryService.recordAccess(sessionId, filePath, currentChecksum);

		return ResponseEntity.ok().eTag(currentChecksum).body(Map.of("type", "context", "data", contextDTO));
	}

	private void requireParam(String value, String name) {
		if (value == null || value.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param '" + name + "' is required");
	}
}
