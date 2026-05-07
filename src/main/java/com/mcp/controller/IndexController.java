package com.mcp.controller;

import com.mcp.dto.ContentSearchResult;
import com.mcp.entity.FileMetadata;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.service.ReconciliationService;
import com.mcp.service.FileScannerService;
import com.mcp.service.LuceneIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/index")
@Tag(name = "Indexing", description = "Endpoints for managing the codebase index")
public class IndexController {

    private final SymbolRepository symbolRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final ReconciliationService reconciliationService;
    private final FileScannerService fileScannerService;
    private final LuceneIndexService luceneIndexService;

    public IndexController(SymbolRepository symbolRepository, 
                           FileMetadataRepository fileMetadataRepository,
                           ReconciliationService reconciliationService,
                           FileScannerService fileScannerService,
                           LuceneIndexService luceneIndexService) {
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.reconciliationService = reconciliationService;
        this.fileScannerService = fileScannerService;
        this.luceneIndexService = luceneIndexService;
    }

    @GetMapping("/{projectId}/status")
    @Operation(summary = "Get index status for a project", description = "Returns statistics about indexed files and symbols for a specific project.")
    public Map<String, Object> getStatus(@PathVariable Long projectId) {
        Map<String, Object> status = new HashMap<>();
        status.put("projectId", projectId);
        status.put("totalFilesIndexed", fileMetadataRepository.countByProjectId(projectId));
        status.put("totalSymbols", symbolRepository.countByProjectId(projectId));
        return status;
    }

    @PostMapping("/{projectId}/trigger-scan")
    @Operation(summary = "Trigger full scan for a project", description = "Manually triggers a full directory scan for a specific project.")
    public String triggerScan(@PathVariable Long projectId) throws IOException {
        fileScannerService.scanProject(projectId);
        return "Scan triggered and completed for project " + projectId;
    }

    @PostMapping("/{projectId}/reconcile")
    @Operation(summary = "Trigger reconciliation for a project", description = "Synchronizes the database with the file system state for a specific project.")
    public String triggerReconcile(@PathVariable Long projectId) {
        reconciliationService.reconcileProject(projectId);
        return "Reconciliation triggered and completed for project " + projectId;
    }

    @GetMapping("/{projectId}/files/search")
    @Operation(summary = "Search files by name/path", description = "Searches for files in a project by path pattern.")
    public List<FileMetadata> searchFiles(@PathVariable Long projectId, @RequestParam String query) {
        return fileMetadataRepository.findByProjectIdAndFilePathContainingIgnoreCase(projectId, query);
    }

    @GetMapping("/{projectId}/search-content")
    @Operation(summary = "Search file content", description = "Performs a full-text search on file contents using Lucene and returns line numbers.")
    public List<ContentSearchResult> searchContent(@PathVariable Long projectId, @RequestParam String query) {
        return luceneIndexService.searchContent(projectId, query);
    }
}
