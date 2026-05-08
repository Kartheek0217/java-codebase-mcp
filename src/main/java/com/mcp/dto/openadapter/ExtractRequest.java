package com.mcp.dto.openadapter;

import java.util.List;

public record ExtractRequest(
                String url,
                List<Selector> selectors) {
        public record Selector(
                        String name,
                        String type,
                        String query,
                        String attribute,
                        Boolean all) {
        }
}
