package com.mcp.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.CreateTaskRequest;
import com.mcp.dto.RuleDTO;
import com.mcp.dto.SessionDTO;
import com.mcp.dto.TaskDTO;
import com.mcp.entity.Skill;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.service.ContextMemoryService;
import com.mcp.service.ProjectRuleService;
import com.mcp.service.SkillService;
import com.mcp.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP", description = "Agentic task management, project rules, and skill management for IDE agent workflows.")
public class McpController {

	private final TaskService taskService;
	private final ProjectRuleService ruleService;
	private final SkillService skillService;
	private final SkillRepository skillRepository;
	private final ProjectRepository projectRepository;
	private final ContextMemoryService contextMemoryService;

	private final Map<String, Session> sessionStore;

	public McpController(TaskService taskService, ProjectRuleService ruleService, SkillService skillService,
			SkillRepository skillRepository, ProjectRepository projectRepository,
			ContextMemoryService contextMemoryService) {
		this.taskService = taskService;
		this.ruleService = ruleService;
		this.skillService = skillService;
		this.skillRepository = skillRepository;
		this.projectRepository = projectRepository;
		this.contextMemoryService = contextMemoryService;
		this.sessionStore = Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
				boolean shouldRemove = size() > 1000;
				if (shouldRemove)
					contextMemoryService.clearSession(eldest.getKey());
				return shouldRemove;
			}
		});
	}

	/**
	 * Cleans up sessions older than 1 hour. Runs every hour via Spring scheduler.
	 */
	@Scheduled(fixedDelay = 3_600_000)
	public void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();
		synchronized (sessionStore) {
			sessionStore.entrySet().removeIf(e -> {
				boolean expired = (now - e.getValue().createdAt()) > 3_600_000L;
				if (expired)
					contextMemoryService.clearSession(e.getKey());
				return expired;
			});
		}
	}

	// ─── Sessions ─────────────────────────────────────────────────────────────

	/**
	 * {@code POST /api/mcp/sessions} : Start a new AI agent session for a project.
	 *
	 * @param projectId Project ID to bind the session to
	 * @return Map with generated sessionId
	 */
	@PostMapping("/sessions")
	@Operation(
		summary = "start-session",
		description = "Start a new AI agent session bound to a project. Sessions track file access history " +
			"and provide context continuity across multiple MCP tool calls. " +
			"Query param: projectId (Long, required). " +
			"Returns {sessionId: string}. Sessions expire after 1 hour of inactivity.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Session created, returns {sessionId}"),
			@ApiResponse(responseCode = "404", description = "Project not found")
		}
	)
	public Map<String, String> startSession(@RequestParam Long projectId) {
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		String sessionId = UUID.randomUUID().toString();
		sessionStore.put(sessionId, new Session(sessionId, projectId));
		return Map.of("sessionId", sessionId);
	}

	/**
	 * {@code GET /api/mcp/sessions/{sessionId}} : Retrieve session metadata and context.
	 *
	 * @param sessionId Session ID returned from start-session
	 * @return SessionDTO with projectId, sessionId, createdAt
	 */
	@GetMapping("/sessions/{sessionId}")
	@Operation(
		summary = "get-session",
		description = "Retrieve metadata for an active agent session. " +
			"Path param: sessionId (string). " +
			"Returns SessionDTO {sessionId, projectId, createdAt, files: []}. " +
			"Use this to verify a session is still active before making context-dependent tool calls.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Session metadata returned"),
			@ApiResponse(responseCode = "404", description = "Session not found or expired")
		}
	)
	public SessionDTO getSession(@PathVariable String sessionId) {
		Session session = sessionStore.get(sessionId);
		if (session == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or expired: " + sessionId);
		return new SessionDTO(session.sessionId(), session.projectId(), session.createdAt(), Collections.emptyList());
	}

	// ─── Tasks ────────────────────────────────────────────────────────────────

	/**
	 * {@code GET /api/mcp/tasks} : List all tasks for a project.
	 *
	 * @param projectId Project ID filter
	 * @return List of TaskDTOs
	 */
	@GetMapping("/tasks")
	@Operation(
		summary = "get-tasks",
		description = "Retrieve all tasks for a project including their steps and status. " +
			"Query param: projectId (Long, required). " +
			"Returns list of TaskDTO {id, projectId, title, description, status, priority, createdAt, updatedAt, steps[]}. " +
			"Use to check the current task list before creating duplicates.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Task list returned")
		}
	)
	public List<TaskDTO> getTasks(@RequestParam Long projectId) {
		return taskService.getTasksByProject(projectId);
	}

	/**
	 * {@code POST /api/mcp/tasks} : Create, update, or delete tasks via {@code X-Op} header.
	 *
	 * @param op      Operation: create | update | delete | update-step
	 * @param id      Task ID — required for update, delete, update-step
	 * @param stepId  Step ID — required for update-step
	 * @param status  Step status string — required for update-step
	 * @param body    Body — CreateTaskRequest for create, TaskDTO for update
	 * @return TaskDTO or void
	 */
	@PostMapping("/tasks")
	@Operation(
		summary = "task-op",
		description = "Create, update, or delete tasks via the X-Op request header:\n\n" +
			"• X-Op: create — Create a new task. Body: CreateTaskRequest " +
				"{projectId (required), title (required), description, priority (HIGH|MEDIUM|LOW), steps: [string]}. " +
				"Returns the created TaskDTO.\n\n" +
			"• X-Op: update — Update an existing task. Query param: id (Long, required). " +
				"Body: TaskDTO with updated fields. Returns updated TaskDTO.\n\n" +
			"• X-Op: delete — Delete a task by ID. Query param: id (Long, required). Returns 204.\n\n" +
			"• X-Op: update-step — Update the status of a single task step. " +
				"Query params: id (task ID, required), stepId (Long, required), status (TODO|IN_PROGRESS|DONE|BLOCKED, required). " +
				"Returns updated TaskDTO with new step status.",
		responses = {
			@ApiResponse(responseCode = "200", description = "TaskDTO returned (create, update, update-step)"),
			@ApiResponse(responseCode = "204", description = "Task deleted (X-Op=delete)"),
			@ApiResponse(responseCode = "400", description = "Missing required param or unknown X-Op"),
			@ApiResponse(responseCode = "404", description = "Task or step not found")
		}
	)
	public Object taskOp(
			@Parameter(description = "Operation: create | update | delete | update-step")
			@RequestHeader(value = "X-Op") String op,
			@RequestParam(required = false) Long id,
			@RequestParam(required = false) Long stepId,
			@RequestParam(required = false) String status,
			@RequestBody(required = false) Object body) {
		return switch (op.toLowerCase()) {
			case "create" -> {
				if (!(body instanceof java.util.Map))
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must be a CreateTaskRequest for op=create");
				// Spring deserializes to LinkedHashMap; re-use Jackson via ObjectMapper binding
				CreateTaskRequest req = convertBody(body, CreateTaskRequest.class);
				yield taskService.createTask(req);
			}
			case "update" -> {
				requireId(id, "update");
				TaskDTO dto = convertBody(body, TaskDTO.class);
				yield taskService.updateTask(id, dto);
			}
			case "delete" -> {
				requireId(id, "delete");
				taskService.deleteTask(id);
				yield null;
			}
			case "update-step" -> {
				requireId(id, "update-step");
				if (stepId == null)
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'stepId' is required for op=update-step");
				if (status == null || status.isBlank())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'status' is required for op=update-step");
				com.mcp.model.TaskStatus taskStatus = com.mcp.model.TaskStatus.valueOf(status.toUpperCase());
				yield taskService.updateStepStatus(id, stepId, taskStatus);
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: create, update, delete, update-step");
		};
	}

	// ─── Rules ────────────────────────────────────────────────────────────────

	/**
	 * {@code GET /api/mcp/rules} : List all rules for a project.
	 *
	 * @param projectId Project ID filter
	 * @return List of RuleDTOs
	 */
	@GetMapping("/rules")
	@Operation(
		summary = "get-rules",
		description = "Retrieve all coding rules associated with a project. " +
			"Query param: projectId (Long, required). " +
			"Returns list of RuleDTO {id, projectId, name, value, category, description}. " +
			"Rules are injected into LLM prompts to enforce project-specific conventions (e.g. JDK version, code style).",
		responses = {
			@ApiResponse(responseCode = "200", description = "Rule list returned")
		}
	)
	public List<RuleDTO> getRules(@RequestParam Long projectId) {
		return ruleService.getRulesByProject(projectId);
	}

	/**
	 * {@code POST /api/mcp/rules} : Create, delete, or clear project rules via {@code X-Op} header.
	 *
	 * @param op        Operation: create | delete | clear
	 * @param id        Rule ID — required for delete
	 * @param projectId Project ID — required for clear
	 * @param body      Body — RuleDTO for create
	 * @return RuleDTO or void
	 */
	@PostMapping("/rules")
	@Operation(
		summary = "rule-op",
		description = "Create, delete, or clear project rules via the X-Op request header:\n\n" +
			"• X-Op: create — Add a new rule. Body: RuleDTO {projectId (required), name (required), value (required), category, description}. " +
				"Returns created RuleDTO.\n\n" +
			"• X-Op: delete — Delete a single rule by ID. Query param: id (Long, required).\n\n" +
			"• X-Op: clear — Delete all rules for a project. Query param: projectId (Long, required).",
		responses = {
			@ApiResponse(responseCode = "200", description = "RuleDTO returned (X-Op=create)"),
			@ApiResponse(responseCode = "204", description = "Rule(s) deleted"),
			@ApiResponse(responseCode = "400", description = "Missing required param or unknown X-Op")
		}
	)
	public Object ruleOp(
			@Parameter(description = "Operation: create | delete | clear")
			@RequestHeader(value = "X-Op") String op,
			@RequestParam(required = false) Long id,
			@RequestParam(required = false) Long projectId,
			@RequestBody(required = false) RuleDTO body) {
		return switch (op.toLowerCase()) {
			case "create" -> {
				if (body == null)
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body (RuleDTO) is required for op=create");
				yield ruleService.createRule(body);
			}
			case "delete" -> {
				requireId(id, "delete");
				ruleService.deleteRule(id);
				yield null;
			}
			case "clear" -> {
				if (projectId == null)
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'projectId' is required for op=clear");
				ruleService.deleteRulesByProject(projectId);
				yield null;
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: create, delete, clear");
		};
	}

	// ─── Skills ───────────────────────────────────────────────────────────────

	/**
	 * {@code GET /api/mcp/skills} : List all skills (global + project-scoped).
	 *
	 * @param projectId Optional project ID; if omitted returns only global built-in skills
	 * @return List of Skill entities
	 */
	@GetMapping("/skills")
	@Operation(
		summary = "get-skills",
		description = "Retrieve available skills for an agent. " +
			"Query param: projectId (Long, optional). " +
			"If projectId is omitted, returns only globally registered built-in skills. " +
			"If projectId is provided, returns global skills plus project-specific learned skills. " +
			"Each Skill has: {id, name, description, content (markdown instructions), project (null if global), source}.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Skill list returned")
		}
	)
	public List<Skill> getSkills(@RequestParam(required = false) Long projectId) {
		List<Skill> globalSkills = skillRepository.findByProjectIdIsNull();
		if (projectId == null) return globalSkills;
		List<Skill> projectSkills = skillRepository.findByProjectId(projectId);
		List<Skill> allSkills = new java.util.ArrayList<>(globalSkills);
		allSkills.addAll(projectSkills);
		return allSkills;
	}

	/**
	 * {@code POST /api/mcp/skills} : Learn a skill or clear project skills via {@code X-Op} header.
	 *
	 * @param op        Operation: learn-url | learn-file | clear
	 * @param projectId Project ID — required for all ops
	 * @param url       Skill URL — required for learn-url
	 * @param filePath  Local file path — required for learn-file
	 * @return Status map or void
	 */
	@PostMapping("/skills")
	@Operation(
		summary = "skill-op",
		description = "Learn a new skill from a URL or local file, or clear all project skills via X-Op:\n\n" +
			"• X-Op: learn-url — Fetch and learn a skill from a URL or built-in path. " +
				"Query params: projectId (required), url (required, e.g. https://example.com/SKILL.md). " +
				"The skill's name and description are parsed from the SKILL.md frontmatter.\n\n" +
			"• X-Op: learn-file — Learn a skill from a local file path on the server. " +
				"Query params: projectId (required), filePath (required, absolute or relative path to SKILL.md).\n\n" +
			"• X-Op: clear — Remove all project-specific learned skills. " +
				"Query param: projectId (required). Global built-in skills are not affected.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Skill learned successfully"),
			@ApiResponse(responseCode = "400", description = "Missing required param or unknown X-Op"),
			@ApiResponse(responseCode = "404", description = "Project not found")
		}
	)
	public Object skillOp(
			@Parameter(description = "Operation: learn-url | learn-file | clear")
			@RequestHeader(value = "X-Op") String op,
			@RequestParam Long projectId,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String filePath) throws IOException {
		return switch (op.toLowerCase()) {
			case "learn-url" -> {
				if (url == null || url.isBlank())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'url' is required for op=learn-url");
				skillService.learnFromUrl(projectId, url);
				yield Map.of("status", "success", "message", "Skill learned from: " + url);
			}
			case "learn-file" -> {
				if (filePath == null || filePath.isBlank())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'filePath' is required for op=learn-file");
				skillService.learnFromFile(projectId, filePath);
				yield Map.of("status", "success", "message", "Skill learned from file: " + filePath);
			}
			case "clear" -> {
				skillService.deleteSkillsByProject(projectId);
				yield Map.of("status", "success", "message", "All project skills cleared for projectId=" + projectId);
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: learn-url, learn-file, clear");
		};
	}

	// ─── Helpers ──────────────────────────────────────────────────────────────

	private void requireId(Long id, String op) {
		if (id == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Query param 'id' is required for op=" + op);
	}

	@SuppressWarnings("unchecked")
	private <T> T convertBody(Object raw, Class<T> type) {
		if (raw == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		// Spring already deserialises to the target type when the parameter is typed
		if (type.isInstance(raw)) return type.cast(raw);
		// Fallback: convert via Jackson ObjectMapper
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		try {
			return mapper.convertValue(raw, type);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Invalid request body for type " + type.getSimpleName() + ": " + e.getMessage());
		}
	}

	/**
	 * Compact record replacing the previous 13-line boilerplate inner class.
	 * {@code createdAt} is epoch millis captured at construction time.
	 */
	private record Session(String sessionId, Long projectId, long createdAt) {
		Session(String sessionId, Long projectId) {
			this(sessionId, projectId, System.currentTimeMillis());
		}
	}
}
