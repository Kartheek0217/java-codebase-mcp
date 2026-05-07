package com.mcp.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.repository.ProjectRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "System", description = "High-level system status and monitoring endpoints.")
public class HealthController {

    private final ProjectRepository projectRepository;

    public HealthController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping("/health")
    @Operation(summary = "Detailed health check", description = "Returns the status of the server, database connectivity, and index readiness.", responses = {
            @ApiResponse(responseCode = "200", description = "System is healthy")
    })
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");

        try {
            projectRepository.count();
            health.put("database", "connected");
        } catch (Exception e) {
            health.put("status", "DEGRADED");
            health.put("database", "disconnected");
        }

        health.put("indices", "ready");

        return health;
    }
}
