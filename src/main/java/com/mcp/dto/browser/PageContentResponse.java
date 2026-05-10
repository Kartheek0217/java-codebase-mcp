package com.mcp.dto.browser;

public record PageContentResponse(
    String url,
    String title,
    String content
) {}
