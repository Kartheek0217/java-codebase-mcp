package com.mcp.dto;

import com.mcp.model.TaskPriority;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for creating a new task")
public record CreateTaskRequest(
    @Schema(description = "ID of the associated project", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    Long projectId,
    @Schema(description = "Title of the task", example = "Optimize Performance", requiredMode = Schema.RequiredMode.REQUIRED)
    String title,
    @Schema(description = "Detailed description", example = "Fix bottlenecks in the indexer.")
    String description,
    @Schema(description = "Priority level", example = "HIGH")
    TaskPriority priority,
    @Schema(description = "List of implementation steps", example = "[\"Profile indexer\", \"Fix cache leak\"]")
    List<String> steps
) {}
