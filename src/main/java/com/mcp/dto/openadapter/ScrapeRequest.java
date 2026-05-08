package com.mcp.dto.openadapter;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScrapeRequest(
                String url,
                String mode,
                @JsonProperty("extract_links") Boolean extractLinks) {
}
