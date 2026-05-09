package com.mcp.dto;

public record WebSearchResultDTO(String url, String title, double score, String snippet, String type) {
}
