package com.mcp.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

	@GetMapping("/tasks")
	@Operation(summary = "list_tasks", description = "Retrieve all tasks for a project. Query param: projectId (Long, required).")
	public List<TaskDTO> getTasks(@RequestParam Long projectId) {
		return taskService.getTasksByProject(projectId);
	}

	@PostMapping("/tasks")
	@Operation(summary = "create_task", description = "Create a new task. Body: CreateTaskRequest.")
	public Object createTask(@RequestBody Object body) {
		if (!(body instanceof java.util.Map))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must be a CreateTaskRequest");
		java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
		Object actualBody = map.containsKey("body") ? map.get("body") : body;
		CreateTaskRequest req = convertBody(actualBody, CreateTaskRequest.class);
		return taskService.createTask(req);
	}

	@PutMapping("/tasks/{id}")
	@Operation(summary = "update_task", description = "Update an existing task. Path param: id. Body: TaskDTO with updated fields.")
	public Object updateTask(@PathVariable Long id, @RequestBody Object body) {
		requireId(id, "update");
		Object actualBody = body;
		if (body instanceof java.util.Map) {
			java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
			if (map.containsKey("body")) {
				actualBody = map.get("body");
			}
		}
		TaskDTO dto = convertBody(actualBody, TaskDTO.class);
		return taskService.updateTask(id, dto);
	}

	@DeleteMapping("/tasks/{id}")
	@Operation(summary = "delete_task", description = "Delete a task by ID. Path param: id.")
	public Object deleteTask(@PathVariable Long id) {
		requireId(id, "delete");
		taskService.deleteTask(id);
		return null;
	}

	@PutMapping("/tasks/{id}/step")
	@Operation(summary = "update_task_step", description = "Update the status of a single task step. Path param: id, Query params: stepId, status.")
	public Object updateTaskStep(@PathVariable Long id, @RequestParam Long stepId, @RequestParam String status) {
		requireId(id, "update-step");
		if (stepId == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'stepId' is required");
		if (status == null || status.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'status' is required");
		TaskStatus taskStatus;
		try {
			taskStatus = TaskStatus.valueOf(status.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status '" + status + "'. Allowed: " + Arrays.toString(TaskStatus.values()));
		}
		return taskService.updateStepStatus(id, stepId, taskStatus);
	}

	// ─── Rules ────────────────────────────────────────────────────────────────

	@GetMapping("/rules")
	@Operation(summary = "list_rules", description = "Retrieve all coding rules associated with a project. Query param: projectId (Long, required).")
	public List<RuleDTO> getRules(@RequestParam Long projectId) {
		return ruleService.getRulesByProject(projectId);
	}

	@PostMapping("/rules")
	@Operation(summary = "create_rule", description = "Add a new rule. Body: RuleDTO.")
	public Object createRule(@RequestBody Object body) {
		if (body == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
		Object actualBody = body;
		if (body instanceof java.util.Map) {
			java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
			if (map.containsKey("body")) {
				actualBody = map.get("body");
			}
		}
		RuleDTO rule = convertBody(actualBody, RuleDTO.class);
		return ruleService.createRule(rule);
	}

	@DeleteMapping("/rules/{id}")
	@Operation(summary = "delete_rule", description = "Delete a single rule by ID. Path param: id.")
	public Object deleteRule(@PathVariable Long id) {
		requireId(id, "delete");
		ruleService.deleteRule(id);
		return null;
	}

	@DeleteMapping("/rules")
	@Operation(summary = "clear_rules", description = "Delete all rules for a project. Query param: projectId.")
	public Object clearRules(@RequestParam Long projectId) {
		if (projectId == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'projectId' is required");
		ruleService.deleteRulesByProject(projectId);
		return null;
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
