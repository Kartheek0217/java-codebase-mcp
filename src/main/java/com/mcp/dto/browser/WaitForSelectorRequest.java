package com.mcp.dto.browser;

/**
 * Request body for waiting until an element matching the selector appears in the DOM.
 */
public record WaitForSelectorRequest(String selector) {}
