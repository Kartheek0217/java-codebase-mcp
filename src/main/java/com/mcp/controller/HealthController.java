package com.mcp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "System")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Get application health", description = "Returns the health status of the application.")
    public Map<String, String> getHealth() {
        return Map.of("status", "UP");
    }
}
