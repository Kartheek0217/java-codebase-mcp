package com.mcp.dto.browser;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record BrowserSessionRequest(
    @Pattern(regexp = "^(chromium|firefox|webkit)$", message = "Invalid browser type. Must be chromium, firefox, or webkit")
    String browserType,
    
    Boolean headless,
    
    @Min(value = 100, message = "viewportWidth must be at least 100")
    @Max(value = 5000, message = "viewportWidth cannot exceed 5000")
    Integer viewportWidth,
    
    @Min(value = 100, message = "viewportHeight must be at least 100")
    @Max(value = 5000, message = "viewportHeight cannot exceed 5000")
    Integer viewportHeight,
    
    @NotNull(message = "projectId is required")
    Long projectId
) {}
