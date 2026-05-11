package com.mcp.dto.browser;

public record LocatorInfo(
        String locator,
        String type,
        String label,
        String name,
        String section,
        String xpath) {
}
