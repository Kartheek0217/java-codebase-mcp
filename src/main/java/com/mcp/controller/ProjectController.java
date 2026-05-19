package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.entity.Project;
import com.mcp.service.GitInfoService;
import com.mcp.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Endpoints for managing multiple codebases/projects. Each project defines a unique root directory to be indexed.")
public class ProjectController {

	private final ProjectService projectService;
	private final GitInfoService gitInfoService;

	public ProjectController(ProjectService projectService, GitInfoService gitInfoService) {
		this.projectService = projectService;
		this.gitInfoService = gitInfoService;
	}

	/**
	 * Creates a new project and begins background indexing.
	 *
	 * @param name     The human-readable name of the project
	 * @param rootPath The absolute filesystem path to the project root
	 * @return The created Project entity
	 * @throws IOException If the root path is invalid or inaccessible
	 */
	@PostMapping
	@Operation(summary = "crt-project", description = "Initializes a new project with a unique name and root directory path. Once created, the system automatically starts background indexing of the directory.", responses = {
			@ApiResponse(responseCode = "200", description = "Project created and indexing started successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid project name or root path") })
	public Project createProject(
			@Parameter(description = "Human-readable name for the project") @RequestParam String name,
			@Parameter(description = "Absolute path to the project's root directory on the local filesystem") @RequestParam String rootPath)
			throws IOException {
		// Validate rootPath before creating project
		Path path = Path.of(rootPath);
		if (!Files.exists(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root path does not exist: " + rootPath);
		}
		if (!Files.isDirectory(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root path is not a directory: " + rootPath);
		}
		if (!Files.isReadable(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root path is not readable: " + rootPath);
		}
		return projectService.createProject(name, rootPath);
	}

	/**
	 * Retrieves all registered projects.
	 *
	 * @return A list of all projects
	 */
	@GetMapping
	@Operation(summary = "get-all-projects", description = "Returns a list of all registered projects and their configurations.")
	public List<Project> getAllProjects(@RequestParam(required = false, defaultValue = "false") boolean summary) {
		return projectService.getAllProjects();
	}

	/**
	 * Retrieves a summary of all projects with statistics.
	 *
	 * @return A list of maps containing project details and counts
	 */
	@GetMapping("/summary")
	@Operation(summary = "get-projects-summ", description = "Returns basic statistics for all projects.")
	public List<Map<String, Object>> getProjectsSummary() {
		return projectService.getAllProjectSummaries();
	}

	/**
	 * Retrieves a project by its unique ID.
	 *
	 * @param id The project ID
	 * @return The project details
	 */
	@GetMapping("/{id}")
	@Operation(summary = "get-project", description = "Get project by ID")
	public Project getProject(@PathVariable Long id) {
		return projectService.getProject(id);
	}

	/**
	 * Retrieves statistics for a specific project.
	 *
	 * @param id The project ID
	 * @return A map containing file and symbol counts
	 */
	@GetMapping("/{id}/stats")
	@Operation(summary = "get-project-stats", description = "Get project stats")
	public Map<String, Object> getProjectStats(@PathVariable Long id) {
		return projectService.getProjectStats(id);
	}

	/**
	 * Retrieves the Git status for a specific project.
	 *
	 * @param id The project ID
	 * @return A map containing modified, added, removed, and untracked files
	 */
	@GetMapping("/{id}/git-status")
	@Operation(summary = "get-project-git-status", description = "Get project Git status")
	public java.util.Map<String, Object> getProjectGitStatus(@PathVariable Long id) {
		return gitInfoService.getProjectStatus(id);
	}

	/**
	 * Stages specific files or patterns in the project's Git repository.
	 *
	 * @param id       The project ID
	 * @param requestBody The request body (either direct list or object with body field)
	 */
	@PostMapping("/{id}/git/stage")
	@Operation(summary = "stage-files", description = "Stage files")
	@SuppressWarnings("unchecked")
	public void stageFiles(@PathVariable Long id, @RequestBody Object requestBody) {
		List<String> patterns;
		if (requestBody instanceof List) {
			patterns = (List<String>) requestBody;
		} else if (requestBody instanceof Map) {
			patterns = (List<String>) ((Map<?, ?>) requestBody).get("body");
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request body");
		}
		gitInfoService.stageFiles(id, patterns);
	}

	/**
	 * Discards local changes for specific files in the project's Git repository.
	 *
	 * @param id       The project ID
	 * @param requestBody The request body (either direct list or object with body field)
	 */
	@PostMapping("/{id}/git/discard")
	@Operation(summary = "discard-changes", description = "Discard changes")
	@SuppressWarnings("unchecked")
	public void discardChanges(@PathVariable Long id, @RequestBody Object requestBody) {
		List<String> patterns;
		if (requestBody instanceof List) {
			patterns = (List<String>) requestBody;
		} else if (requestBody instanceof Map) {
			patterns = (List<String>) ((Map<?, ?>) requestBody).get("body");
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request body");
		}
		gitInfoService.discardChanges(id, patterns);
	}

	/**
	 * Commits staged changes in the project's Git repository.
	 *
	 * @param id      The project ID
	 * @param message The commit message
	 * @return The new commit hash
	 */
	@PostMapping("/{id}/git/commit")
	@Operation(summary = "commit", description = "Commit changes")
	public java.util.Map<String, String> commit(@PathVariable Long id, @RequestParam String message) {
		String hash = gitInfoService.commit(id, message);
		return java.util.Map.of("status", "success", "commitHash", hash);
	}

	/**
	 * Triggers a full re-index of a project.
	 *
	 * @param id The project ID
	 */
	@PostMapping("/{id}/reindex")
	@Operation(summary = "reindex-project", description = "Re-index project")
	public void reindexProject(@PathVariable Long id) {
		projectService.reindexProject(id);
	}

	/**
	 * Deletes a project and all its indexed data.
	 *
	 * @param id The project ID to delete
	 */
	@DeleteMapping("/{id}")
	@Operation(summary = "del-project", description = "Delete a project")
	public void deleteProject(@PathVariable Long id) {
		projectService.deleteProject(id);
	}

}
