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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.CodebaseQuery;
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
import com.mcp.util.LlmResponseOptimizer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
			CodebaseQueryFacade codebaseQueryFacade) {
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
	}

	// ─── Project-scoped read (GET) ────────────────────────────────────────────

	/**
	 * {@code GET /api/codebase/{projectId}} : Read or search codebase data.
	 * Operation selected via {@code X-Op} header.
	 *
	 * @param projectId   Project ID
	 * @param op          Operation name (see description)
	 * @param query       Query parameters record
	 * @param ifNoneMatch Optional ETag for file op
	 * @return Response shape varies by X-Op value
	 */
	@GetMapping("/{projectId}")
	@Operation(summary = "codebase-read", description = "CRITICAL:\n" +
			"1. You MUST pass the actual numeric ID for projectId (e.g., 1), NEVER the literal string '{projectId}'.\n" +
			"2. You MUST provide the X-Op parameter exactly as requested.\n\n" +
			"Read or search codebase data for a project. Select the operation with the X-Op header:\n\n" +
			"• X-Op: file — Read a single file with its symbols and metadata. " +
			"Params: filePath (required), format (full|structure|summary|numbered|markdown, default=full), " +
			"sessionId (optional, records access). " +
			"Supports If-None-Match ETag caching; returns 304 if unchanged.\n\n" +
			"• X-Op: search — Full-text Lucene search across all indexed files. " +
			"Params: query (required), limit (default=10).\n\n" +
			"• X-Op: search-changed — Full-text search restricted to uncommitted (modified/added/staged) files only. " +
			"Params: query (required), limit (default=10).\n\n" +
			"• X-Op: symbols — Search for classes, methods, constructors, or fields by name. " +
			"Params: query (required), type (CLASS|METHOD|FIELD|CONSTRUCTOR, optional), limit (default=50).\n\n" +
			"• X-Op: files — Find indexed files whose paths contain the query string. " +
			"Params: query (required), limit (default=100).\n\n" +
			"• X-Op: suggest — Combined symbol + content search for relevant code context. " +
			"Returns top-10 symbols and top-10 content hits. Params: query (required).\n\n" +
			"• X-Op: history — Return file paths accessed in a session. Params: sessionId (required).\n\n" +
			"• X-Op: topology — Return project package structure and dependency graph. No extra params.\n\n" +
			"• X-Op: summarize — Generate an AI summary of a file. Params: filePath (required).\n\n" +
			"• X-Op: analyze-endpoint — Trace a controller endpoint down to entity level. " +
			"Params: controllerName (required), methodName (required).", responses = {
					@ApiResponse(responseCode = "200", description = "Requested data returned"),
					@ApiResponse(responseCode = "304", description = "File unchanged (ETag match, X-Op=file only)"),
					@ApiResponse(responseCode = "400", description = "Missing required param or unknown X-Op value"),
					@ApiResponse(responseCode = "404", description = "Project or file not found")
			})
	public Object codebaseRead(
			@Parameter(description = "Numeric Project ID (e.g. 1). DO NOT pass '{projectId}'") @PathVariable Long projectId,
			@Parameter(description = "Operation: file | search | search-changed | symbols | files | suggest | history | topology | summarize | analyze-endpoint") @RequestHeader(value = "X-Op") String op,
			CodebaseQuery query,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {

		return switch (op.toLowerCase()) {
			case "file" ->
				getFileContext(projectId, query.filePath(), query.sessionId(), query.getFormatOrDefault(), ifNoneMatch);
			case "search" -> {
				requireParam(query.query(), "query", "search");
				yield luceneIndexService.searchContent(projectId,
						SearchOptions.builder().query(query.query()).limit(query.getLimitOrDefault()).build());
			}
			case "search-changed" -> {
				requireParam(query.query(), "query", "search-changed");
				Project project = projectService.getProject(projectId);
				Set<String> changed = gitInfoService.getChangedFilePaths(projectId);
				Set<String> absPaths = changed.stream()
						.map(rel -> Paths.get(project.getRootPath()).resolve(rel).toAbsolutePath().toString())
						.collect(Collectors.toSet());
				yield luceneIndexService.searchContent(projectId,
						SearchOptions.builder().query(query.query()).filePaths(absPaths)
								.limit(query.getLimitOrDefault()).build());
			}
			case "symbols" -> {
				requireParam(query.query(), "query", "symbols");
				yield codebaseQueryFacade.searchSymbols(projectId, query.query(), query.type(),
						query.getSymbolLimitOrDefault());
			}
			case "files" -> {
				requireParam(query.query(), "query", "files");
				yield codebaseQueryFacade.searchFiles(projectId, query.query(), query.getFileLimitOrDefault());
			}
			case "suggest" -> {
				requireParam(query.query(), "query", "suggest");
				yield codebaseQueryFacade.suggest(projectId, query.query());
			}
			case "history" -> {
				requireParam(query.sessionId(), "sessionId", "history");
				yield contextMemoryService.getSessionFiles(query.sessionId());
			}
			case "topology" -> topologyService.getProjectTopology(projectId);
			case "summarize" -> {
				requireParam(query.filePath(), "filePath", "summarize");
				Project project = projectService.getProject(projectId);
				Path full = Paths.get(project.getRootPath()).resolve(query.filePath());
				String content = Files.readString(full);
				String summary = codeSummarizerService.createIntelligentSummary(content);
				List<Symbol> symbols = fileIndexerService.getSymbols(projectId, full.toString());
				yield Map.of(
						"filePath", query.filePath(),
						"summary", summary,
						"symbols", symbols.stream().map(s -> codebaseQueryFacade.toSymbolDTO(s, null)).toList());
			}
			case "analyze-endpoint" -> {
				requireParam(query.controllerName(), "controllerName", "analyze-endpoint");
				requireParam(query.methodName(), "methodName", "analyze-endpoint");
				yield endpointAnalysisService.analyzeEndpoint(projectId, query.controllerName(), query.methodName());
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: file, search, search-changed, symbols, files, " +
							"suggest, history, topology, summarize, analyze-endpoint");
		};
	}

	// ─── Project-scoped mutations (POST) ─────────────────────────────────────

	/**
	 * {@code POST /api/codebase/{projectId}} : Execute a codebase mutation.
	 * Operation selected via {@code X-Op} header.
	 *
	 * @param projectId Project ID
	 * @param op        Operation name
	 * @param filePaths Body — list of file paths (required when op=batch)
	 * @return Status map or batch result map
	 */
	@PostMapping("/{projectId}")
	@Operation(summary = "codebase-op", description = "CRITICAL:\n" +
			"1. You MUST pass the actual numeric ID for projectId (e.g., 1), NEVER the literal string '{projectId}'.\n" +
			"2. You MUST provide the X-Op parameter exactly as requested.\n\n" +
			"Execute a codebase mutation or heavy read via the X-Op request header:\n\n" +
			"• X-Op: scan — Trigger a directory scan to detect new/changed/deleted files. No body needed.\n\n" +
			"• X-Op: reconcile — Reconcile the symbol index against the current filesystem state. No body needed.\n\n" +
			"• X-Op: batch — Fetch content for multiple files in parallel (uses virtual threads). " +
			"Body: JSON array of relative file paths, e.g. [\"src/main/Foo.java\", \"src/main/Bar.java\"]. " +
			"Returns a map of {filePath → ContextDTO}.", responses = {
					@ApiResponse(responseCode = "200", description = "Operation completed"),
					@ApiResponse(responseCode = "400", description = "Missing body for batch or unknown X-Op value"),
					@ApiResponse(responseCode = "500", description = "Batch aborted on first file error")
			})
	public Object codebaseOp(
			@Parameter(description = "Numeric Project ID (e.g. 1). DO NOT pass '{projectId}'") @PathVariable Long projectId,
			@Parameter(description = "Operation: scan | reconcile | batch") @RequestHeader(value = "X-Op") String op,
			@RequestBody(required = false) Object rawBody) throws IOException {
		return switch (op.toLowerCase()) {
			case "scan" -> {
				fileScannerService.scanProject(projectId);
				yield projectService.buildProjectOpResponse(projectId, "scan", null);
			}
			case "reconcile" -> {
				reconciliationService.reconcileProject(projectId);
				yield projectService.buildProjectOpResponse(projectId, "reconcile", null);
			}
			case "batch" -> {
				List<String> filePaths = null;
				if (rawBody instanceof List<?> list) {
					filePaths = list.stream()
							.filter(String.class::isInstance)
							.map(String.class::cast)
							.toList();
				} else if (rawBody instanceof Map<?, ?> map) {
					if (map.get("body") instanceof List<?> list) {
						filePaths = list.stream()
								.filter(String.class::isInstance)
								.map(String.class::cast)
								.toList();
					}
				}
				if (filePaths == null || filePaths.isEmpty())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Body must be a non-empty list of file paths for op=batch");
				yield codebaseQueryFacade.getBatchContext(projectId, filePaths,
						(pid, fp) -> {
							Object response = getFileContext(pid, fp, null, "full", null);
							return response instanceof ResponseEntity<?> re ? re.getBody() : response;
						});
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: scan, reconcile, batch");
		};
	}

	// ─── Symbol sub-resource ─────────────────────────────────────────────────

	/**
	 * {@code GET /api/codebase/symbols/{id}} : Get symbol data.
	 * Variant selected via {@code X-View} header.
	 *
	 * @param id   Symbol ID
	 * @param view {@code detail} | {@code hierarchy}
	 * @return Symbol detail or call hierarchy map
	 */
	@GetMapping("/symbols/{id}")
	@Operation(summary = "get-symbol", description = "Retrieve symbol data by symbol ID. Select the response shape with X-View:\n\n"
			+
			"• X-View: detail (default) — full Symbol entity (id, name, type, filePath, lineNumber, signature, returnType, modifiers, annotations).\n\n"
			+
			"• X-View: hierarchy — call hierarchy for the symbol: " +
			"{symbol, outgoing: [SymbolCall], incoming: [{call, caller}]}. " +
			"Shows which methods this symbol calls (outgoing) and which callers invoke it (incoming).\n\n" +
			"Path param: id (Long) — symbol ID.", responses = {
					@ApiResponse(responseCode = "200", description = "Symbol data returned"),
					@ApiResponse(responseCode = "404", description = "Symbol not found"),
					@ApiResponse(responseCode = "400", description = "Unknown X-View value")
			})
	public Object getSymbol(
			@PathVariable Long id,
			@Parameter(description = "View variant: 'detail' (default) | 'hierarchy'") @RequestHeader(value = "X-View", required = false, defaultValue = "detail") String view) {
		Symbol symbol = codebaseQueryFacade.getSymbolById(id);
		return switch (view.toLowerCase()) {
			case "detail" -> symbol;
			case "hierarchy" -> codebaseQueryFacade.buildHierarchy(symbol);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-View value '" + view + "'. Allowed: detail, hierarchy");
		};
	}

	// ─── Internal helpers ─────────────────────────────────────────────────────

	private ResponseEntity<Object> getFileContext(Long projectId, String filePath, String sessionId, String format,
			String ifNoneMatch) throws IOException {
		requireParam(filePath, "filePath", "file");

		Project project = projectService.getProject(projectId);

		Path projectRoot = Paths.get(project.getRootPath());
		Path fullPath = projectRoot.resolve(filePath).toAbsolutePath();

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
					.body(Map.of("type", "markdown", "content", LlmResponseOptimizer.toMarkdown(contextDTO)));

		if (sessionId != null)
			contextMemoryService.recordAccess(sessionId, filePath, currentChecksum);

		return ResponseEntity.ok().eTag(currentChecksum).body(Map.of("type", "context", "data", contextDTO));
	}

	private void requireParam(String value, String name, String op) {
		if (value == null || value.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Query param '" + name + "' is required for X-Op=" + op);
	}
}
