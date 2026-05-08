package com.mcp.controller;

import com.mcp.dto.ContentSearchResult;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.Project;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.service.ReconciliationService;
import com.mcp.service.FileScannerService;
import com.mcp.service.LuceneIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/index")
@Tag(name = "Indexing", description = "Endpoints for manual management of the codebase index, including reconciliation and status tracking.")
public class IndexController {

    private final SymbolRepository symbolRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final ReconciliationService reconciliationService;
    private final FileScannerService fileScannerService;
    private final LuceneIndexService luceneIndexService;
    private final ProjectRepository projectRepository;

    public IndexController(SymbolRepository symbolRepository,
            FileMetadataRepository fileMetadataRepository,
            ReconciliationService reconciliationService,
            FileScannerService fileScannerService,
            LuceneIndexService luceneIndexService,
            ProjectRepository projectRepository) {
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.reconciliationService = reconciliationService;
        this.fileScannerService = fileScannerService;
        this.luceneIndexService = luceneIndexService;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/{projectId}/status")
    @Operation(summary = "Get project index statistics", description = "Returns counts of indexed files and AST symbols for a specific project. Useful for verifying index completeness.", responses = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public Map<String, Object> getStatus(
            @Parameter(description = "ID of the project") @PathVariable Long projectId) {
        Map<String, Object> status = new HashMap<>();
        status.put("projectId", projectId);
        status.put("totalFilesIndexed", fileMetadataRepository.countByProjectId(projectId));
        status.put("totalSymbols", symbolRepository.countByProjectId(projectId));
        return status;
    }

    @PostMapping("/{projectId}/trigger-scan")
    @Operation(summary = "Manually trigger directory scan", description = "Forces the system to re-scan the project root directory and update the index with any new or modified files.", responses = {
            @ApiResponse(responseCode = "200", description = "Scan completed successfully")
    })
    public String triggerScan(
            @Parameter(description = "ID of the project to scan") @PathVariable Long projectId) throws IOException {
        fileScannerService.scanProject(projectId);
        return "Scan triggered and completed for project " + projectId;
    }

    @PostMapping("/{projectId}/reconcile")
    @Operation(summary = "Reconcile database with filesystem", description = "Performs a deep sync to remove database entries for files that no longer exist on disk and update entries for modified files.", responses = {
            @ApiResponse(responseCode = "200", description = "Reconciliation completed successfully")
    })
    public String triggerReconcile(
            @Parameter(description = "ID of the project to reconcile") @PathVariable Long projectId) {
        reconciliationService.reconcileProject(projectId);
        return "Reconciliation triggered and completed for project " + projectId;
    }

    @GetMapping("/{projectId}/files/search")
    @Operation(summary = "Search files by path", description = "Finds indexed files whose paths match the provided search query (case-insensitive substring match).", responses = {
            @ApiResponse(responseCode = "200", description = "Files found successfully")
    })
    public List<FileMetadata> searchFiles(
            @Parameter(description = "ID of the project") @PathVariable Long projectId,
            @Parameter(description = "Substring of the file path to search for") @RequestParam String query) {
        return fileMetadataRepository.findByProjectIdAndFilePathContainingIgnoreCase(projectId, query);
    }

    @GetMapping("/{projectId}/search-content")
    @Operation(summary = "Search file content (Lucene)", description = "Performs high-performance full-text search across all indexed files using Lucene. Returns matching lines and context.", responses = {
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    })
    public List<ContentSearchResult> searchContent(
            @Parameter(description = "ID of the project") @PathVariable Long projectId,
            @Parameter(description = "Text query to search for within file contents") @RequestParam String query) {
        return luceneIndexService.searchContent(projectId, query);
    }

    @GetMapping("/{projectId}/files/read")
    @Operation(summary = "Read file content", description = "Retrieves the raw text content of a file within the project.", responses = {
            @ApiResponse(responseCode = "200", description = "File content retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Project or file not found")
    })
    public Map<String, String> readFile(
            @Parameter(description = "ID of the project") @PathVariable Long projectId,
            @Parameter(description = "Relative path of the file from project root") @RequestParam String filePath)
            throws IOException {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        Path projectRoot = Paths.get(project.getRootPath());
        Path fullPath = projectRoot.resolve(filePath).toAbsolutePath();

        if (!Files.exists(fullPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: " + filePath);
        }

        return Map.of(
                "path", filePath,
                "content", Files.readString(fullPath));
    }
}
