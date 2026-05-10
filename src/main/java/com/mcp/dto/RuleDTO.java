package com.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO for Project Rule")
public record RuleDTO(
    @Schema(description = "Unique ID of the rule", example = "1")
    Long id,
    @Schema(description = "ID of the associated project", example = "101")
    Long projectId,
    @Schema(description = "Name of the rule", example = "JDK Version")
    String name,
    @Schema(description = "Value of the rule", example = "21")
    String value,
    @Schema(description = "Category of the rule", example = "java_version")
    String category,
    @Schema(description = "Detailed description of the rule", example = "The project must use JDK 21.")
    String description
) {}
