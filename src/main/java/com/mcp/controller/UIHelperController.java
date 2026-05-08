package com.mcp.controller;

import com.mcp.entity.Project;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui")
@Tag(name = "UI Helpers", description = "Endpoints specifically designed to support the web UI.")
public class UIHelperController {

    private final ProjectRepository projectRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final SymbolRepository symbolRepository;

    public UIHelperController(ProjectRepository projectRepository,
            FileMetadataRepository fileMetadataRepository,
            SymbolRepository symbolRepository) {
        this.projectRepository = projectRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.symbolRepository = symbolRepository;
    }

    @GetMapping("/projects-summary")
    @Operation(summary = "Get a summary of all projects", description = "Returns IDs, names, and basic statistics for all projects to populate UI selectors.")
    public List<Map<String, Object>> getProjectsSummary() {
        List<Project> projects = projectRepository.findAll();
        List<Map<String, Object>> summary = new ArrayList<>();

        for (Project project : projects) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", project.getId());
            item.put("name", project.getName());
            item.put("rootPath", project.getRootPath());
            item.put("fileCount", fileMetadataRepository.countByProjectId(project.getId()));
            item.put("symbolCount", symbolRepository.countByProjectId(project.getId()));
            summary.add(item);
        }
        return summary;
    }

    @GetMapping("/project-stats")
    @Operation(summary = "Get detailed stats for a project by ID")
    public Map<String, Object> getProjectStats(Long projectId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("projectId", projectId);
        stats.put("fileCount", fileMetadataRepository.countByProjectId(projectId));
        stats.put("symbolCount", symbolRepository.countByProjectId(projectId));
        return stats;
    }

    @GetMapping("/symbols/{id}")
    @Operation(summary = "Get symbol details by ID")
    public com.mcp.entity.Symbol getSymbolById(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return symbolRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }
}
