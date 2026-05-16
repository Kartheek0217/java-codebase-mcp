package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.service.ContextMemoryService;
import com.mcp.service.FileIndexerService;
import com.mcp.service.FileScannerService;
import com.mcp.service.GitInfoService;
import com.mcp.service.LuceneIndexService;
import com.mcp.service.ReconciliationService;
import com.mcp.service.TopologyService;
import com.mcp.util.CodeUtils;

import java.util.Set;
import com.mcp.service.CodeSummarizerService;
import com.mcp.service.EndpointAnalysisService;
import com.mcp.util.LlmResponseOptimizer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/codebase")
@Tag(name = "Codebase", description = "Unified endpoints for codebase analysis, search, and context gathering.")
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

	public CodebaseController(FileIndexerService fileIndexerService, FileMetadataRepository fileMetadataRepository,
			SymbolRepository symbolRepository, LuceneIndexService luceneIndexService,
			ProjectRepository projectRepository, TopologyService topologyService,
			ContextMemoryService contextMemoryService, FileScannerService fileScannerService,
			ReconciliationService reconciliationService,
			com.mcp.repository.SymbolCallRepository symbolCallRepository,
			GitInfoService gitInfoService,

			CodeSummarizerService codeSummarizerService,
			EndpointAnalysisService endpointAnalysisService) {
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
	}

	// Fix J: VT-backed executor for parallel batch context fetches
	private static final Executor BATCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	@GetMapping("/{projectId}/file")
	@Operation(summary = "get-file-context", description = "Retrieves the content of a file along with its symbols and metadata.")
	public ResponseEntity<Object> getFileContext(@PathVariable Long projectId, @RequestParam String filePath,
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false, defaultValue = "full") String format,
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {

		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

		Path projectRoot = Paths.get(project.getRootPath());
		Path fullPath = projectRoot.resolve(filePath).toAbsolutePath();

		if (!Files.exists(fullPath)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filePath);
		}

		// Fix A: auto-downgrade to structure for large files (>200 KB) to prevent
		// massive token payloads
		long fileBytes = Files.size(fullPath);
		if (fileBytes > 200_000 && "full".equalsIgnoreCase(format)) {
			format = "structure";
		}

		FileMetadata metadata = fileMetadataRepository.findById(new FileMetadataId(projectId, fullPath.toString()))
				.orElse(null);
		List<Symbol> symbols = fileIndexerService.getSymbols(projectId, fullPath.toString());
		String content = Files.readString(fullPath);
		String finalContent = content;
		String summary = null;

		if ("structure".equalsIgnoreCase(format)) {
			// Fix B: strip Java import blocks — high noise, zero AI signal
			String structureContent = codeSummarizerService.extractStructure(content);
			finalContent = stripJavaImports(structureContent);
		} else if ("summary".equalsIgnoreCase(format)) {
			summary = codeSummarizerService.createIntelligentSummary(content);
			finalContent = null; // No content in summary mode
		} else if ("numbered".equalsIgnoreCase(format)) {
			finalContent = CodeUtils.addLineNumbers(content);
		} else {
			finalContent = content;
		}

		String currentChecksum = metadata != null ? metadata.getChecksum() : null;
		if (ifNoneMatch != null && currentChecksum != null && ifNoneMatch.equals("\"" + currentChecksum + "\"")) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
		}

		ContextDTO contextDTO = new ContextDTO(filePath, finalContent, summary, format,
				symbols.stream().map(this::toSymbolDTO).toList(), toMetadataDTO(metadata), null, false, false);

		if ("markdown".equalsIgnoreCase(format)) {
			return ResponseEntity.ok().eTag(currentChecksum).body(LlmResponseOptimizer.toMarkdown(contextDTO));
		}

		if (sessionId != null) {
			contextMemoryService.recordAccess(sessionId, filePath, currentChecksum);
		}

		return ResponseEntity.ok().eTag(currentChecksum).body(contextDTO);
	}

	@PostMapping("/{projectId}/context/batch")
	@Operation(summary = "get-batch-context", description = "Get batch file context")
	public Map<String, Object> getBatchContext(@PathVariable Long projectId, @RequestBody List<String> filePaths) {
		// Fix J: fetch all files in parallel using Virtual Threads; abort whole batch
		// on first error
		List<CompletableFuture<Map.Entry<String, Object>>> futures = filePaths.stream()
				.map(fp -> CompletableFuture.supplyAsync(() -> {
					try {
						ResponseEntity<Object> response = getFileContext(projectId, fp, null, "full", null);
						return Map.entry(fp, response.getBody() != null ? response.getBody() : "");
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
			// Abort: cancel all remaining futures and surface the error
			futures.forEach(f -> f.cancel(true));
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Batch aborted: " + ex.getCause().getMessage());
		}
		return result;
	}

	@GetMapping("/{projectId}/summarize")
	@Operation(summary = "summarize", description = "Summarize file content")
	public Map<String, Object> summarize(@PathVariable Long projectId, @RequestParam String filePath)
			throws IOException {
		Project project = projectRepository.findById(projectId).orElseThrow();
		Path fullPath = Paths.get(project.getRootPath()).resolve(filePath);
		String content = Files.readString(fullPath);
		String summary = codeSummarizerService.createIntelligentSummary(content);
		List<Symbol> symbols = fileIndexerService.getSymbols(projectId, fullPath.toString());

		Map<String, Object> result = new java.util.HashMap<>();
		result.put("filePath", filePath);
		result.put("summary", summary);
		result.put("symbols", symbols.stream().map(this::toSymbolDTO).toList());
		return result;
	}

	@GetMapping("/{projectId}/search")
	@Operation(summary = "search-codebase", description = "Performs full-text search across all indexed files.")
	public List<ContentSearchResult> search(@PathVariable Long projectId, @RequestParam String query,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		return luceneIndexService.searchContent(projectId, query, limit);
	}

	@GetMapping("/{projectId}/search/changed")
	@Operation(summary = "search-codebase-changed", description = "Performs full-text search ONLY across files that have uncommitted changes (modified, added, or staged).")
	public List<ContentSearchResult> searchChanged(@PathVariable Long projectId, @RequestParam String query,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		Project project = projectRepository.findById(projectId).orElseThrow();
		String rootPath = project.getRootPath();
		Set<String> changedFiles = gitInfoService.getChangedFilePaths(projectId);

		Set<String> absolutePaths = changedFiles.stream()
				.map(relPath -> Paths.get(rootPath).resolve(relPath).toAbsolutePath().toString())
				.collect(java.util.stream.Collectors.toSet());

		return luceneIndexService.searchContent(projectId, query, absolutePaths, limit);
	}

	@GetMapping("/{projectId}/symbols")
	@Operation(summary = "search-symbols", description = "Searches for classes, methods, or fields by name.")
	public List<SymbolDTO> searchSymbols(@PathVariable Long projectId, @RequestParam String query,
			@RequestParam(required = false) String type, @RequestParam(defaultValue = "50") int limit) {
		// Fix L: push limit to DB via Pageable — no more loading all rows then
		// stream-limiting
		PageRequest pageRequest = PageRequest.of(0, limit);
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		String rootPath = project.getRootPath();
		List<Symbol> symbols;
		if (type != null && !type.isEmpty()) {
			try {
				SymbolType symbolType = SymbolType.valueOf(type.toUpperCase());
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCaseAndType(projectId, query,
						symbolType, pageRequest);
			} catch (IllegalArgumentException e) {
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query, pageRequest);
			}
		} else {
			symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query, pageRequest);
		}
		return symbols.stream().map(s -> toSymbolDTO(s, rootPath)).toList();
	}

	@GetMapping("/{projectId}/files")
	@Operation(summary = "search-files", description = "Finds indexed files whose paths match the query.")
	public List<FileMetadataDTO> searchFiles(@PathVariable Long projectId, @RequestParam String query) {
		return fileMetadataRepository.findByProjectIdAndFilePathContainingIgnoreCase(projectId, query).stream()
				.map(this::toMetadataDTO).toList();
	}

	@GetMapping("/{projectId}/suggest")
	@Operation(summary = "suggest", description = "Combines symbol and content search for relevant code snippets.")
	public Map<String, Object> suggest(@PathVariable Long projectId, @RequestParam String query) {
		Map<String, Object> result = new java.util.HashMap<>();
		result.put("symbols", searchSymbols(projectId, query, null, 10));
		result.put("content", search(projectId, query, 10));

		return result;
	}

	@GetMapping("/{projectId}/history")
	@Operation(summary = "get-history", description = "Get file access history")
	public java.util.Set<String> getHistory(@PathVariable Long projectId, @RequestParam String sessionId) {
		return contextMemoryService.getSessionFiles(sessionId);
	}

	@GetMapping("/{projectId}/topology")
	@Operation(summary = "get-topology", description = "Returns project structure and dependencies.")

	public Map<String, Object> getTopology(@PathVariable Long projectId) {
		return topologyService.getProjectTopology(projectId);
	}

	@GetMapping("/{projectId}/analyze-endpoint")
	@Operation(summary = "analyze-endpoint", description = "Analyzes a controller endpoint by tracing implementation from controller to entity level.")
	public com.mcp.entity.Skill analyzeEndpoint(@PathVariable Long projectId, @RequestParam String controllerName,
			@RequestParam String methodName) throws IOException {
		return endpointAnalysisService.analyzeEndpoint(projectId, controllerName, methodName);
	}

	@PostMapping("/{projectId}/scan")
	@Operation(summary = "scan", description = "Trigger directory scan")
	public Map<String, String> scan(@PathVariable Long projectId) throws IOException {
		fileScannerService.scanProject(projectId);
		return Map.of("status", "success");
	}

	@PostMapping("/{projectId}/reconcile")
	@Operation(summary = "reconcile", description = "Reconcile index")
	public Map<String, String> reconcile(@PathVariable Long projectId) {
		reconciliationService.reconcileProject(projectId);
		return Map.of("status", "success");
	}

	@GetMapping("/symbols/{id}")
	@Operation(summary = "get-symbol", description = "Get symbol details")
	public Symbol getSymbol(@PathVariable Long id) {
		return symbolRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	@GetMapping("/symbols/{id}/hierarchy")
	@Operation(summary = "get-call-hierarchy", description = "Get call hierarchy for a symbol")
	public Map<String, Object> getCallHierarchy(@PathVariable Long id) {
		Symbol symbol = symbolRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

		Map<String, Object> result = new java.util.HashMap<>();
		result.put("symbol", symbol);

		List<com.mcp.entity.SymbolCall> outgoing = symbolCallRepository.findByCallerId(id);
		result.put("outgoing", outgoing);

		List<com.mcp.entity.SymbolCall> incoming = symbolCallRepository.findByProjectIdAndCalleeName(
				symbol.getProjectId(),
				symbol.getName());

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
	 * Fix G: Relativize filePath against project rootPath before exposing it in the
	 * DTO.
	 * Absolute paths waste tokens and leak machine-specific directory layout to AI
	 * tools.
	 */
	private SymbolDTO toSymbolDTO(Symbol symbol, String rootPath) {
		String relativePath = symbol.getFilePath();
		if (rootPath != null && relativePath != null && relativePath.startsWith(rootPath)) {
			try {
				relativePath = Paths.get(rootPath).relativize(Paths.get(relativePath)).toString();
			} catch (Exception ignored) {
				/* keep absolute if relativize fails */ }
		}
		return new SymbolDTO(
				symbol.getId(),
				symbol.getName(),
				symbol.getType(),
				relativePath,
				symbol.getLineNumber(),
				symbol.getSignature(),
				symbol.getReturnType(),
				symbol.getModifiers(),
				symbol.getAnnotations());
	}

	// Legacy overload used by getFileContext (which already has the full path
	// context)
	private SymbolDTO toSymbolDTO(Symbol symbol) {
		return toSymbolDTO(symbol, null);
	}

	private FileMetadataDTO toMetadataDTO(FileMetadata metadata) {
		if (metadata == null)
			return null;
		return new FileMetadataDTO(metadata.getFilePath(), metadata.getFileSize(), metadata.getChecksum(),
				metadata.getLastScanned());
	}

	/**
	 * Fix B: Strips Java import statements from content.
	 * Imports are high-noise for AI tools (they see "import org.springframework..."
	 * 30 times)
	 * and contain zero signal about actual logic.
	 */
	private static String stripJavaImports(String content) {
		if (content == null)
			return null;
		return content.lines()
				.filter(line -> !line.stripLeading().startsWith("import "))
				.collect(java.util.stream.Collectors.joining("\n"));
	}
}
