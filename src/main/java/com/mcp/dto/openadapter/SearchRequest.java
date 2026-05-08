package com.mcp.dto.openadapter;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchRequest(
                String query,
                @JsonProperty("num_results") Integer numResults) {
}
