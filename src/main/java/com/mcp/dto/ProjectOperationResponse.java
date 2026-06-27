package com.mcp.dto;

import java.util.Map;

public record ProjectOperationResponse(Long projectId, String op, String status, Map<String, Object> stats) {}
