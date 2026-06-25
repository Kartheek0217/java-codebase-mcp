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
@Tag(name = "Projects", description = "Manage codebases/projects. Each project defines a unique root directory to index.")
public class ProjectController {

	private final ProjectService projectService;
	private final GitInfoService gitInfoService;

	public ProjectController(ProjectService projectService, GitInfoService gitInfoService) {
		this.projectService = projectService;
		this.gitInfoService = gitInfoService;
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
	public Object createProject(
			@Parameter(description = "Human-readable project name") @RequestParam String name,
			@Parameter(description = "Absolute path to the project root on the local filesystem. Examples: 'C:\\path\\to\\project' (Windows), '/path/to/project' (Linux/macOS)") @RequestParam String rootPath)
			throws IOException {
		Path path = Path.of(rootPath);
		if (!Files.exists(path))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root path does not exist: " + rootPath);

		path = path.toRealPath();
		if (!Files.isDirectory(path))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root path is not a directory: " + path);
		if (!Files.isReadable(path))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Root path is not readable: " + path);
		Project project = projectService.createProject(name, path.toString());
		return projectService.buildProjectOpResponse(project.getId(), "create", project);
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
			@Parameter(description = "View variant: 'list' (default) | 'summary'") @RequestParam(required = false, defaultValue = "list") String view) {
		return switch (view.toLowerCase()) {
			case "list" -> projectService.getAllProjects();
			case "summary" -> projectService.getAllProjectSummaries();
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown view value '" + view + "'. Allowed: list, summary");
		};
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
			@PathVariable Long id,
			@Parameter(description = "View variant: 'detail' (default) | 'stats' | 'git-status'") @RequestParam(required = false, defaultValue = "detail") String view) {
		return switch (view.toLowerCase()) {
			case "detail" -> projectService.getProject(id);
			case "stats" -> projectService.getProjectStats(id);
			case "git-status" -> gitInfoService.getProjectStatus(id);
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown view value '" + view + "'. Allowed: detail, stats, git-status");
		};
	}

	// ─── Project-scoped write operations ─────────────────────────────────────

	/**
	 * {@code POST /api/projects/{id}} : Execute a project-scoped write operation.
	 * Operation selected via endpoint path.
	 *
	 * @param id          Project ID
	 * @param op          Operation name
	 * @param requestBody Optional body used by stage/discard
	 * @param message     Commit message (required when op=commit)
	 * @return Operation result map or void
	 */
	@PostMapping("/{id}/reindex")
	@Operation(summary = "reindex_project", description = "Trigger a full re-index of all project files.")
	public Object reindexProject(@PathVariable Long id) {
		projectService.reindexProject(id);
		return projectService.buildProjectOpResponse(id, "reindex", null);
	}

	@PostMapping("/{id}/vcs")
	@Operation(summary = "manage_project_vcs", description = "Execute a VCS operation (stage, discard, commit) on a project.")
	public Object manageProjectVcs(
			@PathVariable Long id,
			@Parameter(description = "Action: 'stage' | 'discard' | 'commit'") @RequestParam String action,
			@RequestBody(required = false) Object requestBody,
			@RequestParam(required = false) String message) {
		return switch (action.toLowerCase()) {
			case "stage" -> {
				gitInfoService.stageFiles(id, parsePatterns(requestBody));
				yield Map.of("status", "success", "op", "stage");
			}
			case "discard" -> {
				gitInfoService.discardChanges(id, parsePatterns(requestBody));
				yield Map.of("status", "success", "op", "discard");
			}
			case "commit" -> {
				if (message == null || message.isBlank())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Query param 'message' is required for action=commit");
				String hash = gitInfoService.commit(id, message);
				yield Map.of("status", "success", "op", "commit", "commitHash", hash);
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown action '" + action + "'. Allowed: stage, discard, commit");
		};
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
	public void deleteProject(@PathVariable Long id) {
		projectService.deleteProject(id);
	}

	// ─── Helpers ──────────────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	private List<String> parsePatterns(Object requestBody) {
		if (requestBody instanceof List<?> list)
			return (List<String>) list;
		if (requestBody instanceof java.util.Map<?, ?> map) {
			Object bodyVal = map.get("body");
			if (bodyVal instanceof List<?> list)
				return (List<String>) list;
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Body must be a list of file patterns or {\"body\": [\"pattern\"]}");
	}
}
