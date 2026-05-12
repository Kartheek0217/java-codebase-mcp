package com.mcp.dto;

import java.util.Map;

public record QuickExtractionResult(
        String url,
        String title,
        String content,
        Map<String, String> metadata,
        String error) {
}
