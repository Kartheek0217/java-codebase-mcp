package com.mcp.dto.browser;

public record BrowserSessionRequest(
    String browserType,
    Boolean headless,
    Integer viewportWidth,
    Integer viewportHeight,
    Long projectId
) {}
