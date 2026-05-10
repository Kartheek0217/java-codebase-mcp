package com.mcp.dto;

import com.mcp.model.TaskPriority;
import com.mcp.model.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO for Project Task")
public record TaskDTO(
    @Schema(description = "Unique ID of the task", example = "1")
    Long id,
    @Schema(description = "ID of the associated project", example = "101")
    Long projectId,
    @Schema(description = "Title of the task", example = "Implement Auth")
    String title,
    @Schema(description = "Detailed description", example = "Add JWT authentication.")
    String description,
    @Schema(description = "Current status")
    TaskStatus status,
    @Schema(description = "Priority level")
    TaskPriority priority,
    @Schema(description = "Creation timestamp")
    LocalDateTime createdAt,
    @Schema(description = "Last update timestamp")
    LocalDateTime updatedAt,
    @Schema(description = "List of implementation steps")
    List<TaskStepDTO> steps
) {}
