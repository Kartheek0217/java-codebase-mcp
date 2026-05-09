package com.mcp.dto;

import java.util.Map;

public record ExtractionResultDTO(String url, Map<String, Object> data, String status) {
}
