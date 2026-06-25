package com.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for a single task within a batch submission")
public record BatchTaskRequest(
        @Schema(description = "Action type (e.g. explain-file, code-review, ask, etc.)", example = "code-review") String action,
        @Schema(description = "Numeric Project ID", example = "1") Long projectId,
        @Schema(description = "Unified action request parameters") AgentActionRequest request) {
}
