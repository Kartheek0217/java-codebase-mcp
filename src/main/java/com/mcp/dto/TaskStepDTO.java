package com.mcp.dto;

import com.mcp.model.TaskStatus;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO for Task Step")
public record TaskStepDTO(
    @Schema(description = "Unique ID of the step", example = "10")
    Long id,
    @Schema(description = "Order of the step", example = "1")
    Integer stepNumber,
    @Schema(description = "Description of the step", example = "Setup database")
    String description,
    @Schema(description = "Current status")
    TaskStatus status,
    @Schema(description = "Completion timestamp")
    LocalDateTime completedAt
) {}
