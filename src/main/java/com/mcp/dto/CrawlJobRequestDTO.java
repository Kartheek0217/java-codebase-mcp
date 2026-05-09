package com.mcp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CrawlJobRequestDTO(@NotNull Long projectId, @NotBlank String startUrl, @Min(0) int maxDepth,
		@Min(1) int maxPages, @Min(0) int delayMs, boolean respectRobotsTxt, List<String> includePatterns,
		List<String> excludePatterns) {
}
