package com.mcp.service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.FileMetadataDTO;
import com.mcp.dto.SearchOptions;
import com.mcp.dto.SymbolDTO;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolCall;
import com.mcp.entity.SymbolType;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolCallRepository;
import com.mcp.repository.SymbolRepository;

/**
 * Facade that encapsulates all repository-level read operations previously
 * leaked directly into {@code CodebaseController}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Symbol search (with optional type filter)
 *   <li>File metadata search by path fragment
 *   <li>File metadata lookup by exact composite key
 *   <li>Call-hierarchy graph construction
 *   <li>Parallel batch file context loading
 *   <li>DTO mapping utilities (symbol path relativization, metadata DTO)
 * </ul>
 */
@Service
public class CodebaseQueryFacade {

    // VT-backed executor for parallel batch context fetches
    private static final Executor BATCH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final SymbolRepository symbolRepository;
    private final SymbolCallRepository symbolCallRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final ProjectRepository projectRepository;
    private final LuceneIndexService luceneIndexService;

    public CodebaseQueryFacade(
            SymbolRepository symbolRepository,
            SymbolCallRepository symbolCallRepository,
            FileMetadataRepository fileMetadataRepository,
            ProjectRepository projectRepository,
            LuceneIndexService luceneIndexService) {
        this.symbolRepository = symbolRepository;
        this.symbolCallRepository = symbolCallRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.projectRepository = projectRepository;
        this.luceneIndexService = luceneIndexService;
    }

    // ─── Symbol search ────────────────────────────────────────────────────────

    /**
     * @implNote Searches symbols by name, optionally filtered by SymbolType.
     * @param projectId Target project
     * @param query     Name fragment (case-insensitive)
     * @param type      Optional SymbolType string (CLASS/METHOD/FIELD/CONSTRUCTOR)
     * @param limit     Maximum results
     * @return List of SymbolDTO with relative file paths
     */
    public List<SymbolDTO> searchSymbols(Long projectId, String query, String type, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        String rootPath = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"))
                .getRootPath();
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

    // ─── File metadata search ─────────────────────────────────────────────────

    /**
     * @implNote Searches file paths by fragment match.
     * @param projectId  Target project
     * @param query      Path fragment (case-insensitive)
     * @param limit      Maximum results
     * @return List of FileMetadataDTO
     */
    public List<FileMetadataDTO> searchFiles(Long projectId, String query, int limit) {
        PageRequest pr = PageRequest.of(0, limit);
        return fileMetadataRepository
                .findByProjectIdAndFilePathContainingIgnoreCase(projectId, query, pr)
                .stream().map(this::toMetadataDTO).toList();
    }

    // ─── Symbol hierarchy ─────────────────────────────────────────────────────

    /**
     * @implNote Builds call hierarchy for a symbol: outgoing (calls made) and incoming (callers).
     * @param symbol The target symbol
     * @return Map with keys: symbol, outgoing, incoming
     */
    public Map<String, Object> buildHierarchy(Symbol symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        List<SymbolCall> outgoing = symbolCallRepository.findByCallerId(symbol.getId());
        result.put("outgoing", outgoing);
        List<SymbolCall> incoming = symbolCallRepository
                .findByProjectIdAndCalleeName(symbol.getProjectId(), symbol.getName());
        List<Map<String, Object>> incomingEnriched = incoming.stream().map(call -> {
            Map<String, Object> item = new HashMap<>();
            item.put("call", call);
            symbolRepository.findById(call.getCallerId()).ifPresent(caller -> item.put("caller", caller));
            return item;
        }).toList();
        result.put("incoming", incomingEnriched);
        return result;
    }

    // ─── Symbol lookup ────────────────────────────────────────────────────────

    /**
     * @implNote Retrieves symbol by ID or throws 404.
     * @param id Symbol ID
     * @return Symbol entity
     */
    public Symbol getSymbolById(Long id) {
        return symbolRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Symbol not found: " + id));
    }

    // ─── Batch fetch ──────────────────────────────────────────────────────────

    /**
     * @implNote Parallel virtual-thread batch file context fetch.
     *           Uses BATCH_EXECUTOR (VT-per-task). Fails fast on first error.
     * @param projectId Project context
     * @param filePaths List of relative file paths
     * @param fileContextLoader Callback to load a single file context
     * @return Map of filePath → context result
     */
    public Map<String, Object> getBatchContext(Long projectId, List<String> filePaths,
            FileSingleContextLoader fileContextLoader) {
        List<CompletableFuture<Map.Entry<String, Object>>> futures = filePaths.stream()
                .map(fp -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Object result = fileContextLoader.load(projectId, fp);
                        return Map.entry(fp, result != null ? result : "");
                    } catch (IOException e) {
                        throw new CompletionException("Failed to read file: " + fp, e);
                    }
                }, BATCH_EXECUTOR))
                .toList();

        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
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

    /**
     * Functional interface for loading a single file context, used by
     * {@link #getBatchContext}.
     */
    @FunctionalInterface
    public interface FileSingleContextLoader {
        Object load(Long projectId, String filePath) throws IOException;
    }

    // ─── Suggest (symbol + content combined) ──────────────────────────────────

    /**
     * @implNote Combined symbol + Lucene content search for AI suggest workflow.
     * @param projectId Target project
     * @param query     Search term
     * @return Map with keys: symbols, content
     */
    public Map<String, Object> suggest(Long projectId, String query) {
        return Map.of(
                "symbols", searchSymbols(projectId, query, null, 10),
                "content", luceneIndexService.searchContent(projectId,
                        SearchOptions.builder().query(query).limit(10).build()));
    }

    // ─── Lucene content search pass-through ───────────────────────────────────

    /**
     * @implNote Delegates to LuceneIndexService using canonical SearchOptions API.
     * @param projectId Project ID
     * @param opts      Search options
     * @return List of content search results
     */
    public List<ContentSearchResult> searchContent(Long projectId, SearchOptions opts) {
        return luceneIndexService.searchContent(projectId, opts);
    }

    // ─── File metadata lookup ─────────────────────────────────────────────────

    /**
     * @implNote Retrieves file metadata by exact composite key (projectId + absolutePath).
     * @param projectId    Target project
     * @param absolutePath Absolute file path as stored in the index
     * @return FileMetadataDTO or null if not indexed
     */
    public FileMetadataDTO getFileMetadata(Long projectId, String absolutePath) {
        FileMetadataId id = new FileMetadataId(projectId, absolutePath);
        return fileMetadataRepository.findById(id).map(this::toMetadataDTO).orElse(null);
    }

    // ─── DTO mappers ──────────────────────────────────────────────────────────

    /**
     * Relativizes filePath against project rootPath before exposing in DTO.
     * Absolute paths waste tokens and leak machine-specific layout to AI tools.
     *
     * @param symbol   Source symbol
     * @param rootPath Project root (pass null to skip relativization)
     * @return SymbolDTO with relative path
     */
    public SymbolDTO toSymbolDTO(Symbol symbol, String rootPath) {
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

    /**
     * @implNote Maps FileMetadata entity to DTO.
     * @param metadata Source entity (may be null)
     * @return FileMetadataDTO or null
     */
    public FileMetadataDTO toMetadataDTO(FileMetadata metadata) {
        if (metadata == null) return null;
        return new FileMetadataDTO(metadata.getFilePath(), metadata.getFileSize(),
                metadata.getChecksum(), metadata.getLastScanned());
    }
}
