package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
import com.mcp.service.SemanticSearchService;
import java.util.Set;
import com.mcp.service.CodeSummarizerService;
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
	private final SemanticSearchService semanticSearchService;
	private final CodeSummarizerService codeSummarizerService;

	public CodebaseController(FileIndexerService fileIndexerService, FileMetadataRepository fileMetadataRepository,
			SymbolRepository symbolRepository, LuceneIndexService luceneIndexService,
			ProjectRepository projectRepository, TopologyService topologyService,
			ContextMemoryService contextMemoryService, FileScannerService fileScannerService,
			ReconciliationService reconciliationService,
			com.mcp.repository.SymbolCallRepository symbolCallRepository,
			GitInfoService gitInfoService,
			SemanticSearchService semanticSearchService,
			CodeSummarizerService codeSummarizerService) {
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
		this.semanticSearchService = semanticSearchService;
		this.codeSummarizerService = codeSummarizerService;
	}

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

		FileMetadata metadata = fileMetadataRepository.findById(new FileMetadataId(projectId, fullPath.toString()))
				.orElse(null);
		List<Symbol> symbols = fileIndexerService.getSymbols(projectId, fullPath.toString());
		String content = Files.readString(fullPath);
		String finalContent = content;
		String summary = null;

		if ("structure".equalsIgnoreCase(format)) {
			finalContent = codeSummarizerService.extractStructure(content);
		} else if ("summary".equalsIgnoreCase(format)) {
			summary = codeSummarizerService.createIntelligentSummary(content);
			finalContent = null; // No content in summary mode
		} else {
			finalContent = CodeUtils.addLineNumbers(content);
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
	public Map<String, ContextDTO> getBatchContext(@PathVariable Long projectId, @RequestBody List<String> filePaths) {
		Map<String, ContextDTO> result = new java.util.HashMap<>();
		for (String filePath : filePaths) {
			try {
				ResponseEntity<Object> response = getFileContext(projectId, filePath, null, "compact", null);
				if (response.getStatusCode() == HttpStatus.OK) {
					result.put(filePath, (ContextDTO) response.getBody());
				}
			} catch (Exception e) {
				// Skip failed files
			}
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
		List<Symbol> symbols;
		if (type != null && !type.isEmpty()) {
			try {
				SymbolType symbolType = SymbolType.valueOf(type.toUpperCase());
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCaseAndType(projectId, query,
						symbolType);
			} catch (IllegalArgumentException e) {
				symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query);
			}
		} else {
			symbols = symbolRepository.findByProjectIdAndNameContainingIgnoreCase(projectId, query);
		}
		return symbols.stream().limit(limit).map(this::toSymbolDTO).toList();
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
		result.put("semantic", searchSemantic(projectId, query, 10));
		return result;
	}

	@GetMapping("/{projectId}/search/semantic")
	@Operation(summary = "search-codebase-semantic", description = "Performs semantic search using vector similarity.")
	public List<Map<String, Object>> searchSemantic(@PathVariable Long projectId, @RequestParam String query,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		return semanticSearchService.searchSemantic(query, limit).stream()
				.map(sv -> {
					Map<String, Object> map = new java.util.HashMap<>();
					map.put("symbol", toSymbolDTO(sv.getSymbol()));
					map.put("content", sv.getContent());
					return map;
				}).toList();
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

	private SymbolDTO toSymbolDTO(Symbol symbol) {
		return new SymbolDTO(symbol.getId(), symbol.getName(), symbol.getType(), symbol.getFilePath());
	}

	private FileMetadataDTO toMetadataDTO(FileMetadata metadata) {
		if (metadata == null)
			return null;
		return new FileMetadataDTO(metadata.getFilePath(), metadata.getFileSize(), metadata.getChecksum(),
				metadata.getLastScanned());
	}
}
