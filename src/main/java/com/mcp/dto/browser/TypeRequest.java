package com.mcp.dto.browser;

/**
 * Request body for typing text character-by-character into an element.
 * Uses Playwright's pressSequentially which simulates real keystrokes.
 */
public record TypeRequest(String selector, String text) {}
