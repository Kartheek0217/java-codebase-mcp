package com.mcp.controller;

import com.mcp.dto.CreateTaskRequest;
import com.mcp.dto.TaskDTO;
import com.mcp.model.TaskStatus;
import com.mcp.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/tasks")
@Tag(name = "Task Management", description = "Endpoints for managing implementation tasks and steps.")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "Get all tasks for project")
    public List<TaskDTO> getTasks(@RequestParam Long projectId) {
        return taskService.getTasksByProject(projectId);
    }

    @PostMapping
    @Operation(summary = "Create a new task")
    public TaskDTO createTask(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update task")
    public TaskDTO updateTask(@PathVariable Long id, @RequestBody TaskDTO taskDTO) {
        return taskService.updateTask(id, taskDTO);
    }

    @PutMapping("/{id}/steps/{stepId}")
    @Operation(summary = "Update step status")
    public TaskDTO updateStepStatus(
            @PathVariable Long id,
            @PathVariable Long stepId,
            @RequestParam TaskStatus status) {
        return taskService.updateStepStatus(id, stepId, status);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete task")
    public Map<String, String> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Map.of("status", "success");
    }
}
