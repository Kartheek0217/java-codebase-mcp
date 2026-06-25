package com.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response details for a single task within a batch submission")
public record BatchTaskResponse(
        @Schema(description = "ID of the created task", example = "123") Long taskId,
        @Schema(description = "Action type requested", example = "code-review") String action,
        @Schema(description = "Initial task status", example = "PENDING") String status) {
}
