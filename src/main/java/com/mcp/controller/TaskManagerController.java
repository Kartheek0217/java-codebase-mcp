package com.mcp.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.dto.CreateTaskRequest;
import com.mcp.dto.RuleDTO;
import com.mcp.dto.TaskDTO;
import com.mcp.model.TaskStatus;
import com.mcp.service.ProjectRuleService;
import com.mcp.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "Tasks & Rules", description = "Agentic task management and project rules for IDE agent workflows.")
public class TaskManagerController {

	private final TaskService taskService;
	private final ProjectRuleService ruleService;
	private final ObjectMapper objectMapper;

	public TaskManagerController(TaskService taskService, ProjectRuleService ruleService, ObjectMapper objectMapper) {
		this.taskService = taskService;
		this.ruleService = ruleService;
		this.objectMapper = objectMapper;
	}

	// ─── Tasks ────────────────────────────────────────────────────────────────

	/**
	 * {@code GET /api/mcp/tasks} : List project tasks.
	 * 
	 * @implNote Delegates to taskService
	 * @param projectId
	 * @return List of TaskDTO
	 * @author JCB
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
	 * {@code POST /api/mcp/tasks} : Execute task operation.
	 * 
	 * @implNote Routes create/update/delete/update-step to taskService
	 * @param op, id, stepId, status, body
	 * @return Object
	 * @author JCB
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
				java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
				Object actualBody = map.containsKey("body") ? map.get("body") : body;
				CreateTaskRequest req = convertBody(actualBody, CreateTaskRequest.class);
				yield taskService.createTask(req);
			}
			case "update" -> {
				requireId(id, "update");
				Object actualBody = body;
				if (body instanceof java.util.Map) {
					java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
					if (map.containsKey("body")) {
						actualBody = map.get("body");
					}
				}
				TaskDTO dto = convertBody(actualBody, TaskDTO.class);
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
				TaskStatus taskStatus;
				try {
					taskStatus = TaskStatus.valueOf(status.toUpperCase());
				} catch (IllegalArgumentException e) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status '" + status + "'. Allowed: " + Arrays.toString(TaskStatus.values()));
				}
				yield taskService.updateStepStatus(id, stepId, taskStatus);
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: create, update, delete, update-step");
		};
	}

	// ─── Rules ────────────────────────────────────────────────────────────────

	/**
	 * {@code GET /api/mcp/rules} : List project rules.
	 * 
	 * @implNote Delegates to ruleService
	 * @param projectId
	 * @return List of RuleDTO
	 * @author JCB
	 */
	@GetMapping("/rules")
	@Operation(
		summary = "get-rules",
		description = "Retrieve all coding rules associated with a project. " +
			"Query param: projectId (Long, required). " +
			"Returns list of RuleDTO {id, projectId, name, value, category, description}. " +
			"Rules are injected into AGENT prompts to enforce project-specific conventions (e.g. JDK version, code style).",
		responses = {
			@ApiResponse(responseCode = "200", description = "Rule list returned")
		}
	)
	public List<RuleDTO> getRules(@RequestParam Long projectId) {
		return ruleService.getRulesByProject(projectId);
	}

	/**
	 * {@code POST /api/mcp/rules} : Execute rule operation.
	 * 
	 * @implNote Routes create/delete/clear to ruleService
	 * @param op, id, projectId, body
	 * @return Object
	 * @author JCB
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
			@RequestBody(required = false) Object body) {
		return switch (op.toLowerCase()) {
			case "create" -> {
				if (body == null)
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required for op=create");
				Object actualBody = body;
				if (body instanceof java.util.Map) {
					java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
					if (map.containsKey("body")) {
						actualBody = map.get("body");
					}
				}
				RuleDTO rule = convertBody(actualBody, RuleDTO.class);
				yield ruleService.createRule(rule);
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

	// ─── Helpers ──────────────────────────────────────────────────────────────

	private void requireId(Long id, String op) {
		if (id == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Query param 'id' is required for op=" + op);
	}

	private <T> T convertBody(Object raw, Class<T> type) {
		if (raw == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		// Spring already deserialises to the target type when the parameter is typed
		if (type.isInstance(raw)) return type.cast(raw);
		// Fallback: convert via Spring-managed Jackson ObjectMapper
		try {
			return objectMapper.convertValue(raw, type);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Invalid request body for type " + type.getSimpleName() + ": " + e.getMessage());
		}
	}
}
