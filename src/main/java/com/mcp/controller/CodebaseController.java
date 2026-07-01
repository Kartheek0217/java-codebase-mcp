package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;

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

import com.mcp.dto.SearchOptions;
import com.mcp.entity.Symbol;
import com.mcp.service.CodebaseQueryFacade;
import com.mcp.service.EndpointAnalysisService;
import com.mcp.service.FileScannerService;
import com.mcp.service.LuceneIndexService;
import com.mcp.service.ProjectService;
import com.mcp.service.ReconciliationService;
import com.mcp.service.TopologyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import com.mcp.dto.ProjectOperationResponse;
import io.github.overrridee.annotation.ResponseEnvelope;
import io.github.overrridee.annotation.IgnoreEnvelope;

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
@Validated
@ResponseEnvelope
@Tag(name = "Codebase", description = "Unified endpoints for codebase analysis, file reading, search, and index management.")
public class CodebaseController {

	private final ProjectService projectService;
	private final TopologyService topologyService;
	private final FileScannerService fileScannerService;
	private final ReconciliationService reconciliationService;
	private final EndpointAnalysisService endpointAnalysisService;
	private final CodebaseQueryFacade codebaseQueryFacade;
	private final LuceneIndexService luceneIndexService;

	public CodebaseController(ProjectService projectService,
			TopologyService topologyService,
			FileScannerService fileScannerService,
			ReconciliationService reconciliationService,
			EndpointAnalysisService endpointAnalysisService,
			CodebaseQueryFacade codebaseQueryFacade,
			LuceneIndexService luceneIndexService) {
		this.projectService = projectService;
		this.topologyService = topologyService;
		this.fileScannerService = fileScannerService;
		this.reconciliationService = reconciliationService;
		this.endpointAnalysisService = endpointAnalysisService;
		this.codebaseQueryFacade = codebaseQueryFacade;
		this.luceneIndexService = luceneIndexService;
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<Map<String, String>> handleIOException(IOException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("status", "ERROR", "message", ex.getMessage()));
	}

	// ─── Project-scoped read (GET) ────────────────────────────────────────────

	@GetMapping("/file")
	@Operation(summary = "read_file", description = "Read a single file with its symbols and metadata.")
	@IgnoreEnvelope(reason = "ETag/304 handling")
	public ResponseEntity<Object> readFile(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Relative path to the file") @RequestParam @NotBlank String filePath,
			@Parameter(description = "Format (full|structure|summary|numbered|markdown)", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"full", "structure", "summary", "numbered", "markdown"})) 
			@RequestParam(required = false, defaultValue = "full") @Pattern(regexp = "^(full|structure|summary|numbered|markdown)$", message = "Invalid format") String format,
			@Parameter(description = "Session ID") @RequestParam(required = false) String sessionId,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {
		Path path = Paths.get(filePath).normalize();
		if (path.toString().contains("..") || path.isAbsolute()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "ERROR", "message", "Invalid file path"));
		}
		CodebaseQueryFacade.FileContextResult result = codebaseQueryFacade.getFileContext(
				projectId, filePath, sessionId, format, ifNoneMatch);
		if (result.statusCode() == 304) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}
		return ResponseEntity.status(result.statusCode())
				.eTag(result.checksum())
				.body(result.body());
	}

	@GetMapping("/search")
	@Operation(summary = "search_content", description = "Full-text Lucene search across all indexed files.")
	public Object searchContent(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Search query") @RequestParam @NotBlank String query,
			@Parameter(description = "Result limit (1-100)") @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(100) Integer limit) throws IOException {
		return luceneIndexService.searchContent(projectId,
				SearchOptions.builder().query(query).limit(limit).build());
	}

	@GetMapping("/search-changed")
	@Operation(summary = "search_changed_content", description = "Full-text search restricted to uncommitted (modified/added/staged) files only.")
	public Object searchChangedContent(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Search query") @RequestParam @NotBlank String query,
			@Parameter(description = "Result limit (1-100)") @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(100) Integer limit) throws IOException {
		return codebaseQueryFacade.searchChangedContent(projectId, query, limit);
	}

	@GetMapping("/symbols")
	@Operation(summary = "search_symbols", description = "Search for classes, methods, constructors, or fields by name.")
	public Object searchSymbols(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Search query") @RequestParam @NotBlank String query,
			@Parameter(description = "Symbol type (CLASS|METHOD|FIELD|CONSTRUCTOR)", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"CLASS", "METHOD", "FIELD", "CONSTRUCTOR"})) 
			@RequestParam(required = false) String type,
			@Parameter(description = "Result limit (1-100)") @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(100) Integer limit) {
		return codebaseQueryFacade.searchSymbols(projectId, query, type, limit);
	}

	@GetMapping("/files")
	@Operation(summary = "find_files", description = "Find indexed files whose paths contain the query string.")
	public Object findFiles(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Search query") @RequestParam @NotBlank String query,
			@Parameter(description = "Result limit (1-100)") @RequestParam(required = false, defaultValue = "100") @Min(1) @Max(100) Integer limit) {
		return codebaseQueryFacade.searchFiles(projectId, query, limit);
	}

	@GetMapping("/suggest")
	@Operation(summary = "suggest_context", description = "Combined symbol + content search for relevant code context. Returns top-10 symbols and top-10 content hits.")
	public Object suggestContext(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Search query") @RequestParam @NotBlank String query) throws IOException {
		return codebaseQueryFacade.suggest(projectId, query);
	}

	@GetMapping("/history")
	@Operation(summary = "get_session_history", description = "Return file paths accessed in a session.")
	public Object getSessionHistory(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Session ID") @RequestParam @NotBlank String sessionId) {
		return codebaseQueryFacade.getSessionHistory(sessionId);
	}

	@GetMapping("/topology")
	@Operation(summary = "get_project_topology", description = "Return project package structure and dependency graph.")
	public Object getProjectTopology(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId) {
		return topologyService.getProjectTopology(projectId);
	}

	@GetMapping("/summarize")
	@Operation(summary = "summarize_file", description = "Generate an AI summary of a file.")
	public Object summarizeFile(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Relative file path") @RequestParam @NotBlank String filePath) throws IOException {
		return codebaseQueryFacade.summarizeFile(projectId, filePath);
	}

	@GetMapping("/analyze-endpoint")
	@Operation(summary = "analyze_endpoint", description = "Trace a controller endpoint down to entity level.")
	public Object analyzeEndpoint(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Controller name") @RequestParam @NotBlank String controllerName,
			@Parameter(description = "Method name") @RequestParam @NotBlank String methodName) throws IOException {
		return endpointAnalysisService.analyzeEndpoint(projectId, controllerName, methodName);
	}

	// ─── Project-scoped mutations (POST) ─────────────────────────────────────

	@PostMapping("/scan")
	@Operation(summary = "scan_project", description = "Trigger a directory scan to detect new/changed/deleted files.")
	public ProjectOperationResponse scanProject(@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId) {
		fileScannerService.scanProject(projectId);
		Map<String, Object> res = projectService.buildProjectOpResponse(projectId, "scan", null);
		@SuppressWarnings("unchecked")
		Map<String, Object> stats = (Map<String, Object>) res.get("stats");
		return new ProjectOperationResponse(
				projectId,
				(String) res.get("op"),
				(String) res.get("status"),
				stats
		);
	}

	@PostMapping("/reconcile")
	@Operation(summary = "reconcile_index", description = "Reconcile the symbol index against the current filesystem state.")
	public ProjectOperationResponse reconcileIndex(@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId) {
		reconciliationService.reconcileProject(projectId);
		Map<String, Object> res = projectService.buildProjectOpResponse(projectId, "reconcile", null);
		@SuppressWarnings("unchecked")
		Map<String, Object> stats = (Map<String, Object>) res.get("stats");
		return new ProjectOperationResponse(
				projectId,
				(String) res.get("op"),
				(String) res.get("status"),
				stats
		);
	}

	@PostMapping("/batch")
	@Operation(summary = "batch_read_files", description = "Fetch content for multiple files in parallel.")
	public Object batchReadFiles(
			@Parameter(description = "Numeric Project ID", required = true) @RequestHeader(value = "projectId", required = true) @NotNull Long projectId,
			@Parameter(description = "Batch Read Payload (Array of paths or object with filePaths)") @RequestBody @NotBlank String rawPayload) throws IOException {
		return codebaseQueryFacade.getBatchContextParsed(projectId, rawPayload);
	}

	// ─── Symbol sub-resource ─────────────────────────────────────────────────

	@GetMapping("/symbols/{id}")
	@Operation(summary = "get_symbol_detail", description = "Retrieve full Symbol entity by ID.")
	public Object getSymbolDetail(@Parameter(description = "Numeric Symbol ID") @PathVariable @NotNull Long id) {
		return codebaseQueryFacade.getSymbolById(id);
	}

	@GetMapping("/symbols/{id}/hierarchy")
	@Operation(summary = "get_call_hierarchy", description = "Retrieve call hierarchy for the symbol.")
	public Object getCallHierarchy(@Parameter(description = "Numeric Symbol ID") @PathVariable @NotNull Long id) {
		Symbol symbol = codebaseQueryFacade.getSymbolById(id);
		return codebaseQueryFacade.buildHierarchy(symbol);
	}
}
