package com.mcp.controller;

import java.util.List;


import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.RuleDTO;
import com.mcp.dto.TaskDTO;
import com.mcp.service.ProjectRuleService;
import com.mcp.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import io.github.overrridee.annotation.ResponseEnvelope;
import io.github.overrridee.annotation.IgnoreEnvelope;

@RestController
@RequestMapping("/api/mcp")
@Validated
@ResponseEnvelope
@Tag(name = "Tasks & Rules", description = "Agentic task management and project rules for IDE agent workflows.")
public class TaskManagerController {

	private final TaskService taskService;
	private final ProjectRuleService ruleService;

	public TaskManagerController(TaskService taskService, ProjectRuleService ruleService) {
		this.taskService = taskService;
		this.ruleService = ruleService;
	}

	// ─── Tasks ────────────────────────────────────────────────────────────────

	@GetMapping("/tasks")
	@Operation(summary = "list_tasks", description = "Retrieve all tasks for a project. Query param: projectId (Long, required).")
	public List<TaskDTO> getTasks(@RequestParam("projectId") @NotNull Long projectId) {
		return taskService.getTasksByProject(projectId);
	}

	@PostMapping("/tasks")
	@Operation(summary = "create_task", description = "Create a new task. Body: CreateTaskRequest.")
	public Object createTask(@RequestBody Object body) {
		return taskService.createTask(body);
	}

	@PutMapping("/tasks/{id}")
	@Operation(summary = "update_task", description = "Update an existing task. Path param: id. Body: TaskDTO with updated fields.")
	public Object updateTask(@PathVariable Long id, @RequestBody Object body) {
		return taskService.updateTask(id, body);
	}

	@DeleteMapping("/tasks/{id}")
	@Operation(summary = "delete_task", description = "Delete a task by ID. Path param: id.")
	@IgnoreEnvelope(reason = "204 No Content")
	public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
		taskService.deleteTask(id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/tasks/{id}/step")
	@Operation(summary = "update_task_step", description = "Update the status of a single task step. Path param: id, Query params: stepId, status.")
	public Object updateTaskStep(
			@PathVariable Long id, 
			@RequestParam @NotNull Long stepId, 
			@RequestParam @NotBlank String status) {
		return taskService.updateStepStatus(id, stepId, status);
	}

	// ─── Rules ────────────────────────────────────────────────────────────────

	@GetMapping("/rules")
	@Operation(summary = "list_rules", description = "Retrieve all coding rules associated with a project. Query param: projectId (Long, required).")
	public List<RuleDTO> getRules(@RequestParam("projectId") @NotNull Long projectId) {
		return ruleService.getRulesByProject(projectId);
	}

	@PostMapping("/rules")
	@Operation(summary = "create_rule", description = "Add a new rule. Body: RuleDTO.")
	public Object createRule(@RequestBody Object body) {
		return ruleService.createRule(body);
	}

	@DeleteMapping("/rules/{id}")
	@Operation(summary = "delete_rule", description = "Delete a single rule by ID. Path param: id.")
	@IgnoreEnvelope(reason = "204 No Content")
	public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
		ruleService.deleteRule(id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/rules")
	@Operation(summary = "clear_rules", description = "Delete all rules for a project. Query param: projectId.")
	@IgnoreEnvelope(reason = "204 No Content")
	public ResponseEntity<Void> clearRules(@RequestParam("projectId") @NotNull Long projectId) {
		ruleService.deleteRulesByProject(projectId);
		return ResponseEntity.noContent().build();
	}
}
