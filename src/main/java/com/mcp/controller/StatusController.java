package com.mcp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "System", description = "System status and monitoring")
public class StatusController {

    @GetMapping("/status")
    @Operation(summary = "Get application status", description = "Returns a simple status message indicating the app is running.")
    public Map<String, String> getStatus() {
        return Map.of(
            "status", "OK",
            "version", "0.0.1-SNAPSHOT",
            "engine", "Java Codebase MCP"
        );
    }
}
