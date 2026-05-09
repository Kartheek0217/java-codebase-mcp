package com.mcp.dto;

import java.time.LocalDateTime;

import com.mcp.model.CrawlStatus;

public record CrawlJobResponseDTO(Long id, Long projectId, String startUrl, CrawlStatus status, int pagesCrawled,
		LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime completedAt) {
}
