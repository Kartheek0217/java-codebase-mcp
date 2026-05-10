package com.mcp.dto.browser;

/**
 * Request body for selecting a dropdown option by value.
 */
public record SelectOptionRequest(String selector, String value) {}
