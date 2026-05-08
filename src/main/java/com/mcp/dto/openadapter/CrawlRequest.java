package com.mcp.dto.openadapter;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrawlRequest(
        String url,
        @JsonProperty("max_pages") Integer maxPages,
        @JsonProperty("max_depth") Integer maxDepth) {
}
