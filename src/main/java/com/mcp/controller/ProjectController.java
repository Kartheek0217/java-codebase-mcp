package com.mcp.controller;

import com.mcp.entity.Project;
import com.mcp.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Endpoints for managing multiple codebases/projects. Each project defines a unique root directory to be indexed.")
public class ProjectController {

    private final ProjectService projectService;
    private final com.mcp.service.GitInfoService gitInfoService;

    public ProjectController(ProjectService projectService, com.mcp.service.GitInfoService gitInfoService) {
        this.projectService = projectService;
        this.gitInfoService = gitInfoService;
    }

    @PostMapping
    @Operation(summary = "Create a new project", description = "Initializes a new project with a unique name and root directory path. Once created, the system automatically starts background indexing of the directory.", responses = {
            @ApiResponse(responseCode = "200", description = "Project created and indexing started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid project name or root path")
    })
    public Project createProject(
            @Parameter(description = "Human-readable name for the project") @RequestParam String name,
            @Parameter(description = "Absolute path to the project's root directory on the local filesystem") @RequestParam String rootPath)
            throws IOException {
        return projectService.createProject(name, rootPath);
    }

    @GetMapping
    @Operation(summary = "List all projects", description = "Returns a list of all registered projects and their configurations.", responses = {
            @ApiResponse(responseCode = "200", description = "List of projects retrieved successfully")
    })
    public List<Project> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID", description = "Retrieves details for a specific project by its unique numeric ID.", responses = {
            @ApiResponse(responseCode = "200", description = "Project details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public Project getProject(
            @Parameter(description = "Unique ID of the project") @PathVariable Long id) {
        return projectService.getProject(id);
    }

    @GetMapping("/{id}/git-status")
    @Operation(summary = "Get project Git status", description = "Returns the Git repository status for the specified project.", responses = {
            @ApiResponse(responseCode = "200", description = "Git status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Project not found or Git repository missing")
    })
    public java.util.Map<String, Object> getProjectGitStatus(
            @Parameter(description = "Unique ID of the project") @PathVariable Long id) {
        return gitInfoService.getProjectStatus(id);
    }

    @PostMapping("/{id}/git/stage")
    @Operation(summary = "Stage files", description = "Adds files to the Git index.")
    public void stageFiles(@PathVariable Long id, @RequestBody List<String> patterns) {
        gitInfoService.stageFiles(id, patterns);
    }

    @PostMapping("/{id}/git/discard")
    @Operation(summary = "Discard changes", description = "Reverts local modifications in the specified files.")
    public void discardChanges(@PathVariable Long id, @RequestBody List<String> patterns) {
        gitInfoService.discardChanges(id, patterns);
    }

    @PostMapping("/{id}/git/commit")
    @Operation(summary = "Commit changes", description = "Creates a new Git commit for the specified project.")
    public String commit(@PathVariable Long id, @RequestParam String message) {
        return gitInfoService.commit(id, message);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project", description = "Removes the project registration and permanently deletes all associated indexed metadata, symbols, and Lucene search indices. Does NOT delete the actual source files.", responses = {
            @ApiResponse(responseCode = "200", description = "Project and indices deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public void deleteProject(
            @Parameter(description = "Unique ID of the project to delete") @PathVariable Long id) {
        projectService.deleteProject(id);
    }
}
