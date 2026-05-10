package com.mcp.dto.browser;

import java.time.LocalDateTime;

public record BrowserSessionResponse(
    String sessionId,
    String status,
    String currentUrl,
    LocalDateTime createdAt
) {}
