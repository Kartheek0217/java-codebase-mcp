package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.ContextDTO;
import com.mcp.dto.FileMetadataDTO;
import com.mcp.dto.SymbolDTO;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Project;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolType;
import com.mcp.properties.CodebaseProperties;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.service.CodeSummarizerService;
import com.mcp.service.ContextMemoryService;
import com.mcp.service.EndpointAnalysisService;
import com.mcp.service.FileIndexerService;
import com.mcp.service.FileScannerService;
import com.mcp.service.GitInfoService;
import com.mcp.service.LuceneIndexService;
import com.mcp.service.ReconciliationService;
import com.mcp.service.TopologyService;
import com.mcp.util.CodeUtils;
import com.mcp.util.LlmResponseOptimizer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/codebase")
@Tag(name = "Codebase", description = "Unified endpoints for codebase analysis, file reading, search, and index management.")
public class CodebaseController {

	private final FileIndexerService fileIndexerService;
	private final FileMetadataRepository fileMetadataRepository;
	private final SymbolRepository symbolRepository;
	private final LuceneIndexService luceneIndexService;
	private final ProjectRepository projectRepository;
	private final TopologyService topologyService;
	private final ContextMemoryService contextMemoryService;
	private final FileScannerService fileScannerService;
	private final ReconciliationService reconciliationService;
	private final com.mcp.repository.SymbolCallRepository symbolCallRepository;
	private final GitInfoService gitInfoService;
	private final CodeSummarizerService codeSummarizerService;
	private final EndpointAnalysisService endpointAnalysisService;
	private final CodebaseProperties codebaseProperties;

	// VT-backed executor for parallel batch context fetches
	private static final Executor BATCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	public CodebaseController(FileIndexerService fileIndexerService,
			FileMetadataRepository fileMetadataRepository,
			SymbolRepository symbolRepository,
			LuceneIndexService luceneIndexService,
			ProjectRepository projectRepository,
			TopologyService topologyService,
			ContextMemoryService contextMemoryService,
			FileScannerService fileScannerService,
			ReconciliationService reconciliationService,
			com.mcp.repository.SymbolCallRepository symbolCallRepository,
			GitInfoService gitInfoService,
			CodeSummarizerService codeSummarizerService,
			EndpointAnalysisService endpointAnalysisService,
			CodebaseProperties codebaseProperties) {
		this.fileIndexerService = fileIndexerService;
		this.fileMetadataRepository = fileMetadataRepository;
		this.symbolRepository = symbolRepository;
		this.luceneIndexService = luceneIndexService;
		this.projectRepository = projectRepository;
		this.topologyService = topologyService;
		this.contextMemoryService = contextMemoryService;
		this.fileScannerService = fileScannerService;
		this.reconciliationService = reconciliationService;
		this.symbolCallRepository = symbolCallRepository;
		this.gitInfoService = gitInfoService;
		this.codeSummarizerService = codeSummarizerService;
		this.endpointAnalysisService = endpointAnalysisService;
		this.codebaseProperties = codebaseProperties;
	}

	// ─── Project-scoped read (GET) ────────────────────────────────────────────

	/**
	 * {@code GET /api/codebase/{projectId}} : Read or search codebase data.
	 * Operation selected via {@code X-Op} header.
	 *
	 * @param projectId Project ID
	 * @param op        Operation name (see description)
	 * @param filePath  Required for: file, summarize, analyze-endpoint
	 * @param query     Required for: search, search-changed, symbols, files, suggest
	 * @param sessionId Optional for: file (record access), history (required)
	 * @param format    Optional for: file — full | structure | summary | numbered | markdown (default: full)
	 * @param type      Optional for: symbols — CLASS | METHOD | FIELD | CONSTRUCTOR
	 * @param limit     Optional for: search, search-changed, symbols, files (default varies)
	 * @param controllerName Required for: analyze-endpoint
	 * @param methodName     Required for: analyze-endpoint
	 * @param ifNoneMatch    Optional ETag for: file
	 * @return Response shape varies by X-Op value
	 */
	@GetMapping("/{projectId}")
	@Operation(
		summary = "codebase-read",
		description = "Read or search codebase data for a project. Select the operation with the X-Op header:\n\n" +
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
				"Params: controllerName (required), methodName (required).",
		responses = {
			@ApiResponse(responseCode = "200", description = "Requested data returned"),
			@ApiResponse(responseCode = "304", description = "File unchanged (ETag match, X-Op=file only)"),
			@ApiResponse(responseCode = "400", description = "Missing required param or unknown X-Op value"),
			@ApiResponse(responseCode = "404", description = "Project or file not found")
		}
	)
	public Object codebaseRead(
			@PathVariable Long projectId,
			@Parameter(description = "Operation: file | search | search-changed | symbols | files | suggest | history | topology | summarize | analyze-endpoint")
			@RequestHeader(value = "X-Op") String op,
			@RequestParam(required = false) String filePath,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false, defaultValue = "full") String format,
			@RequestParam(required = false) String type,
			@RequestParam(required = false, defaultValue = "10") int limit,
			@RequestParam(required = false) String controllerName,
			@RequestParam(required = false) String methodName,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {

		return switch (op.toLowerCase()) {
			case "file" -> getFileContext(projectId, filePath, sessionId, format, ifNoneMatch);
			case "search" -> {
				requireParam(query, "query", "search");
				yield luceneIndexService.searchContent(projectId, query, limit);
			}
			case "search-changed" -> {
				requireParam(query, "query", "search-changed");
				Project project = projectRepository.findById(projectId).orElseThrow();
				Set<String> changed = gitInfoService.getChangedFilePaths(projectId);
				Set<String> absPaths = changed.stream()
						.map(rel -> Paths.get(project.getRootPath()).resolve(rel).toAbsolutePath().toString())
						.collect(java.util.stream.Collectors.toSet());
				yield luceneIndexService.searchContent(projectId, query, absPaths, limit);
			}
			case "symbols" -> {
				requireParam(query, "query", "symbols");
				yield searchSymbols(projectId, query, type, limit);
			}
			case "files" -> {
				requireParam(query, "query", "files");
				PageRequest pr = PageRequest.of(0, limit);
				yield fileMetadataRepository
						.findByProjectIdAndFilePathContainingIgnoreCase(projectId, query, pr)
						.stream().map(this::toMetadataDTO).toList();
			}
			case "suggest" -> {
				requireParam(query, "query", "suggest");
				yield Map.of(
						"symbols", searchSymbols(projectId, query, null, 10),
						"content", luceneIndexService.searchContent(projectId, query, 10));
			}
			case "history" -> {
				requireParam(sessionId, "sessionId", "history");
				yield contextMemoryService.getSessionFiles(sessionId);
			}
			case "topology" -> topologyService.getProjectTopology(projectId);
			case "summarize" -> {
				requireParam(filePath, "filePath", "summarize");
				Project project = projectRepository.findById(projectId).orElseThrow();
				Path full = Paths.get(project.getRootPath()).resolve(filePath);
				String content = Files.readString(full);
				String summary = codeSummarizerService.createIntelligentSummary(content);
				List<Symbol> symbols = fileIndexerService.getSymbols(projectId, full.toString());
				yield Map.of(
						"filePath", filePath,
						"summary", summary,
						"symbols", symbols.stream().map(s -> toSymbolDTO(s, null)).toList());
			}
			case "analyze-endpoint" -> {
				requireParam(controllerName, "controllerName", "analyze-endpoint");
				requireParam(methodName, "methodName", "analyze-endpoint");
				yield endpointAnalysisService.analyzeEndpoint(projectId, controllerName, methodName);
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
	 * @param projectId   Project ID
	 * @param op          Operation name
	 * @param filePaths   Body — list of file paths (required when op=batch)
	 * @return Status map or batch result map
	 */
	@PostMapping("/{projectId}")
	@Operation(
		summary = "codebase-op",
		description = "Execute a codebase mutation or heavy read via the X-Op request header:\n\n" +
			"• X-Op: scan — Trigger a directory scan to detect new/changed/deleted files. No body needed.\n\n" +
			"• X-Op: reconcile — Reconcile the symbol index against the current filesystem state. No body needed.\n\n" +
			"• X-Op: batch — Fetch content for multiple files in parallel (uses virtual threads). " +
				"Body: JSON array of relative file paths, e.g. [\"src/main/Foo.java\", \"src/main/Bar.java\"]. " +
				"Returns a map of {filePath → ContextDTO}.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Operation completed"),
			@ApiResponse(responseCode = "400", description = "Missing body for batch or unknown X-Op value"),
			@ApiResponse(responseCode = "500", description = "Batch aborted on first file error")
		}
	)
	public Object codebaseOp(
			@PathVariable Long projectId,
			@Parameter(description = "Operation: scan | reconcile | batch")
			@RequestHeader(value = "X-Op") String op,
			@RequestBody(required = false) List<String> filePaths) throws IOException {
		return switch (op.toLowerCase()) {
			case "scan" -> {
				fileScannerService.scanProject(projectId);
				yield Map.of("status", "success", "op", "scan");
			}
			case "reconcile" -> {
				reconciliationService.reconcileProject(projectId);
				yield Map.of("status", "success", "op", "reconcile");
			}
			case "batch" -> {
				if (filePaths == null || filePaths.isEmpty())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must be a non-empty list of file paths for op=batch");
				yield getBatchContext(projectId, filePaths);
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
	@Operation(
		summary = "get-symbol",
		description = "Retrieve symbol data by symbol ID. Select the response shape with X-View:\n\n" +
			"• X-View: detail (default) — full Symbol entity (id, name, type, filePath, lineNumber, signature, returnType, modifiers, annotations).\n\n" +
			"• X-View: hierarchy — call hierarchy for the symbol: " +
				"{symbol, outgoing: [SymbolCall], incoming: [{call, caller}]}. " +
				"Shows which methods this symbol calls (outgoing) and which callers invoke it (incoming).\n\n" +
			"Path param: id (Long) — symbol ID.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Symbol data returned"),
			@ApiResponse(responseCode = "404", description = "Symbol not found"),
			@ApiResponse(responseCode = "400", description = "Unknown X-View value")
		}
	)
	public Object getSymbol(
			@PathVariable Long id,
			@Parameter(description = "View variant: 'detail' (default) | 'hierarchy'")
			@RequestHeader(value = "X-View", required = false, defaultValue = "detail") String view) {
		Symbol symbol = symbolRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Symbol not found: " + id));
		return switch (view.toLowerCase()) {
			case "detail" -> symbol;
			case "hierarchy" -> buildHierarchy(symbol);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-View value '" + view + "'. Allowed: detail, hierarchy");
		};
	}

	// ─── Internal helpers ─────────────────────────────────────────────────────

	private Object getFileContext(Long projectId, String filePath, String sessionId, String format,
			String ifNoneMatch) throws IOException {
		requireParam(filePath, "filePath", "file");

		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

		Path projectRoot = Paths.get(project.getRootPath());
		Path fullPath = projectRoot.resolve(filePath).toAbsolutePath();

		if (!Files.exists(fullPath))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filePath);

		// Auto-downgrade to structure for large files to prevent massive token payloads
		long fileBytes = Files.size(fullPath);
		long maxBytes = (long) codebaseProperties.getMaxFileSizeKb() * 1024;
		if (fileBytes > maxBytes && "full".equalsIgnoreCase(format))
			format = "structure";

		FileMetadata metadata = fileMetadataRepository
				.findById(new FileMetadataId(projectId, fullPath.toString())).orElse(null);
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

		String currentChecksum = metadata != null ? metadata.getChecksum() : null;
		if (ifNoneMatch != null && currentChecksum != null && ifNoneMatch.equals("\"" + currentChecksum + "\""))
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();

		ContextDTO contextDTO = new ContextDTO(filePath, finalContent, summary, format,
				symbols.stream().map(s -> toSymbolDTO(s, null)).toList(), toMetadataDTO(metadata), null, false, false);

		if ("markdown".equalsIgnoreCase(format))
			return ResponseEntity.ok().eTag(currentChecksum).body(LlmResponseOptimizer.toMarkdown(contextDTO));

		if (sessionId != null)
			contextMemoryService.recordAccess(sessionId, filePath, currentChecksum);

		return ResponseEntity.ok().eTag(currentChecksum).body(contextDTO);
	}

	private Map<String, Object> getBatchContext(Long projectId, List<String> filePaths) {
		List<CompletableFuture<Map.Entry<String, Object>>> futures = filePaths.stream()
				.map(fp -> CompletableFuture.supplyAsync(() -> {
					try {
						Object response = getFileContext(projectId, fp, null, "full", null);
						Object body = response instanceof ResponseEntity<?> re ? re.getBody() : response;
						return Map.entry(fp, body != null ? body : "");
					} catch (IOException e) {
						throw new CompletionException("Failed to read file: " + fp, e);
					}
				}, BATCH_EXECUTOR))
				.toList();

		Map<String, Object> result = new LinkedHashMap<>();
		try {
			for (CompletableFuture<Map.Entry<String, Object>> f : futures) {
				Map.Entry<String, Object> entry = f.join();
				result.put(entry.getKey(), entry.getValue());
			}
		} catch (CompletionException ex) {
			futures.forEach(f -> f.cancel(true));
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Batch aborted: " + ex.getCause().getMessage());
		}
		return result;
	}

	private List<SymbolDTO> searchSymbols(Long projectId, String query, String type, int limit) {
		PageRequest pageRequest = PageRequest.of(0, limit);
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		String rootPath = project.getRootPath();
		List<Symbol> symbols;
		if (type != null && !type.isEmpty()) {
			try {
				SymbolType symbolType = SymbolType.valueOf(type.toUpperCase());
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCaseAndType(
						projectId, query, symbolType, pageRequest);
			} catch (IllegalArgumentException e) {
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query, pageRequest);
			}
		} else {
			symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query, pageRequest);
		}
		return symbols.stream().map(s -> toSymbolDTO(s, rootPath)).toList();
	}

	private Map<String, Object> buildHierarchy(Symbol symbol) {
		Map<String, Object> result = new java.util.HashMap<>();
		result.put("symbol", symbol);
		List<com.mcp.entity.SymbolCall> outgoing = symbolCallRepository.findByCallerId(symbol.getId());
		result.put("outgoing", outgoing);
		List<com.mcp.entity.SymbolCall> incoming = symbolCallRepository
				.findByProjectIdAndCalleeName(symbol.getProjectId(), symbol.getName());
		List<Map<String, Object>> incomingEnriched = incoming.stream().map(call -> {
			Map<String, Object> item = new java.util.HashMap<>();
			item.put("call", call);
			symbolRepository.findById(call.getCallerId()).ifPresent(caller -> item.put("caller", caller));
			return item;
		}).toList();
		result.put("incoming", incomingEnriched);
		return result;
	}

	/**
	 * Relativize filePath against project rootPath before exposing it in the DTO.
	 * Absolute paths waste tokens and leak machine-specific directory layout to AI tools.
	 * Pass {@code null} for rootPath to skip relativization.
	 */
	private SymbolDTO toSymbolDTO(Symbol symbol, String rootPath) {
		String relativePath = symbol.getFilePath();
		if (rootPath != null && relativePath != null && relativePath.startsWith(rootPath)) {
			try {
				relativePath = Paths.get(rootPath).relativize(Paths.get(relativePath)).toString();
			} catch (Exception ignored) { /* keep absolute if relativize fails */ }
		}
		return new SymbolDTO(
				symbol.getId(), symbol.getName(), symbol.getType(), relativePath,
				symbol.getLineNumber(), symbol.getSignature(), symbol.getReturnType(),
				symbol.getModifiers(), symbol.getAnnotations());
	}

	private FileMetadataDTO toMetadataDTO(FileMetadata metadata) {
		if (metadata == null) return null;
		return new FileMetadataDTO(metadata.getFilePath(), metadata.getFileSize(),
				metadata.getChecksum(), metadata.getLastScanned());
	}

	private void requireParam(String value, String name, String op) {
		if (value == null || value.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Query param '" + name + "' is required for X-Op=" + op);
	}
}
