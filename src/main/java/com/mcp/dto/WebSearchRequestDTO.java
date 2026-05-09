package com.mcp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebSearchRequestDTO(@NotNull Long projectId, @NotBlank String query, String site, @Min(1) int limit,
		int offset) {
}
