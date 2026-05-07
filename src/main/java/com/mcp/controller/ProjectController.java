package com.mcp.controller;

import com.mcp.entity.Project;
import com.mcp.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Endpoints for managing multiple projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a new project", description = "Initializes a new project with a root directory and starts indexing.")
    public Project createProject(@RequestParam String name, @RequestParam String rootPath) throws IOException {
        return projectService.createProject(name, rootPath);
    }

    @GetMapping
    @Operation(summary = "List all projects")
    public List<Project> getAllProjects() {
        return projectService.getAllProjects();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public Project getProject(@PathVariable Long id) {
        return projectService.getProject(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project", description = "Removes the project and all its associated indexed data.")
    public void deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
    }
}
