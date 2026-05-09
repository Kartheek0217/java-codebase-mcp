package com.mcp.dto;

public record AccessEntry(Long projectId, String type, String value, long timestamp) {
}
