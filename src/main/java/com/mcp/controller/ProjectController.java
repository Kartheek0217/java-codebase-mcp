package com.mcp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.mcp.entity.Project;
import com.mcp.dto.ProjectOperationResponse;
import com.mcp.dto.VcsOperationResponse;
import io.github.overrridee.annotation.ResponseEnvelope;
import io.github.overrridee.annotation.IgnoreEnvelope;

@RestController
@RequestMapping("/api/projects")
@Validated
@ResponseEnvelope
@Tag(name = "Projects", description = "Manage codebases/projects. Each project defines a unique root directory to index.")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<ProjectOperationResponse> handleIOException(IOException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ProjectOperationResponse(null, "ERROR", ex.getMessage(), null));
	}

	// ─── Collection endpoints ─────────────────────────────────────────────────

	/**
	 * {@code POST /api/projects} : Create a new project and begin background
	 * indexing.
	 *
	 * @param name     Human-readable project name
	 * @param rootPath Absolute filesystem path to the project root
	 * @return Created Project entity
	 * @throws IOException If the root path is invalid or inaccessible
	 */
	@PostMapping
	@Operation(summary = "crt-project", description = "Create a new project and start background indexing of its root directory. "
			+
			"Required params: name (string), rootPath (absolute path). " +
			"Returns the created Project object with status=INDEXING.", responses = {
					@ApiResponse(responseCode = "200", description = "Project created and indexing started"),
					@ApiResponse(responseCode = "400", description = "rootPath missing, not a directory, or not readable")
			})
	public Project createProject(
			@Parameter(description = "Human-readable project name") @RequestParam @NotBlank String name,
			@Parameter(description = "Absolute path to the project root on the local filesystem. Examples: 'C:\\path\\to\\project' (Windows), '/path/to/project' (Linux/macOS)") @RequestParam @NotBlank String rootPath)
			throws IOException {
		Path path = Paths.get(rootPath).toAbsolutePath().normalize();
		if (path.toString().contains("..")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rootPath: path traversal detected");
		}
		if (!Files.exists(path) || !Files.isDirectory(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Invalid rootPath: directory does not exist or is not readable");
		}
		try {
			return (Project) projectService.createProjectAndIndex(name, path.toString());
		} catch (ClassCastException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Service returned unexpected type", e);
		}
	}

	/**
	 * {@code GET /api/projects} : List projects. Behaviour controlled by
	 * {@code X-View} header.
	 *
	 * @param view {@code list} (default) — all projects; {@code summary} — projects
	 *             with file/symbol counts
	 * @return List of projects or summary maps
	 */
	@GetMapping
	@Operation(summary = "list_projects", description = "Retrieve project list. Optional view parameter can be 'list' or 'summary'.")
	public Object getProjects(
			@Parameter(description = "View variant: 'list' (default) | 'summary'", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {
					"list",
					"summary" })) @RequestParam(required = false, defaultValue = "list") @NotBlank String view) {
		return projectService.getProjects(view);
	}

	// ─── Project-scoped read ──────────────────────────────────────────────────

	/**
	 * {@code GET /api/projects/{id}} : Read project data. Variant selected via
	 * {@code X-View} header.
	 *
	 * @param id   Project ID
	 * @param view {@code detail} | {@code stats} | {@code git-status}
	 * @return Project detail, stats map, or git-status map
	 */
	@GetMapping("/{id}")
	@Operation(summary = "get_project_details", description = "Read project data. Optional view parameter can be 'detail', 'stats', or 'git-status'.")
	public Object getProject(
			@PathVariable @NotNull Long id,
			@Parameter(description = "View variant: 'detail' (default) | 'stats' | 'git-status'", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {
					"detail", "stats",
					"git-status" })) @RequestParam(required = false, defaultValue = "detail") @NotBlank String view) {
		return projectService.getProject(id, view);
	}

	// ─── Project-scoped write operations ─────────────────────────────────────

	/**
	 * {@code POST /api/projects/{id}} : Execute a project-scoped write operation.
	 * Operation selected via endpoint path.
	 *
	 * @param id Project ID
	 * @return Operation result map or void
	 */
	@PostMapping("/{id}/reindex")
	@Operation(summary = "reindex_project", description = "Trigger a full re-index of all project files.")
	public ProjectOperationResponse reindexProject(@PathVariable @NotNull Long id) {
		projectService.reindexProject(id);
		Map<String, Object> res = projectService.buildProjectOpResponse(id, "reindex", null);
		@SuppressWarnings("unchecked")
		Map<String, Object> stats = (Map<String, Object>) res.get("stats");
		return new ProjectOperationResponse(
				id,
				(String) res.get("op"),
				(String) res.get("status"),
				stats);
	}

	@PostMapping("/{id}/vcs")
	@Operation(summary = "manage_project_vcs", description = "Execute a VCS operation (stage, discard, commit) on a project.")
	public VcsOperationResponse manageProjectVcs(
			@PathVariable @NotNull Long id,
			@Parameter(description = "Action: 'stage' | 'discard' | 'commit'", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {
					"stage", "discard", "commit" })) @RequestParam @NotBlank String action,
			@RequestBody(required = false) Object requestBody,
			@RequestParam(required = false) String message) {
		@SuppressWarnings("unchecked")
		Map<String, Object> res = (Map<String, Object>) projectService.manageProjectVcs(id, action,
				requestBody, message);
		return new VcsOperationResponse(
				id,
				(String) res.get("action"),
				(String) res.get("status"),
				(String) res.get("commitHash"));
	}

	// ─── Delete ───────────────────────────────────────────────────────────────

	/**
	 * {@code DELETE /api/projects/{id}} : Delete a project and all its indexed
	 * data.
	 *
	 * @param id Project ID to delete
	 */
	@DeleteMapping("/{id}")
	@Operation(summary = "del-project", description = "Permanently delete a project and remove all its indexed symbols, files, and metadata. "
			+
			"Path param: id (Long) — project ID. Returns 204 No Content on success.", responses = {
					@ApiResponse(responseCode = "204", description = "Project deleted"),
					@ApiResponse(responseCode = "404", description = "Project not found")
			})
	@IgnoreEnvelope(reason = "204 No Content")
	public ResponseEntity<Void> deleteProject(@PathVariable @NotNull Long id) {
		projectService.deleteProject(id);
		return ResponseEntity.noContent().build();
	}
}
