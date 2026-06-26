package com.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemStatusDTO(
    String status,
    String database,
    String commit,
    String branch,
    Boolean available,
    String baseUrl,
    String model,
    Integer timeoutSeconds,
    Integer maxTokens,
    Boolean reachable
) {}
