package com.mcp.dto;

public record VcsOperationResponse(Long projectId, String action, String status, String commitHash) {}
