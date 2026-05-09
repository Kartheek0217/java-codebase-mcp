package com.mcp.dto;

import java.util.List;

public record SessionDTO(String sessionId, Long projectId, long createdAt, List<AccessEntry> history) {
}
