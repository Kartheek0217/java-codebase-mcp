package com.mcp.controller;

import com.mcp.repository.ProjectRepository;
import com.mcp.service.GitInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "System", description = "High-level system status and monitoring endpoints. Used to verify overall health and versioning.")
public class StatusController {

    private final ProjectRepository projectRepository;
    private final GitInfoService gitInfoService;

    public StatusController(ProjectRepository projectRepository, GitInfoService gitInfoService) {
        this.projectRepository = projectRepository;
        this.gitInfoService = gitInfoService;
    }

    @GetMapping("/status")
    @Operation(
        summary = "Get system status and metadata", 
        description = "Returns application status, version, uptime, and metadata about the server's own Git repository. Also includes the total number of managed projects.",
        responses = {
            @ApiResponse(responseCode = "200", description = "System status retrieved successfully")
        }
    )
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "OK");
        status.put("version", "0.0.1-SNAPSHOT");
        status.put("engine", "Java Codebase MCP");
        status.put("gitCommit", gitInfoService.getCommitHash());
        status.put("gitBranch", gitInfoService.getBranchName());
        status.put("gitMessage", gitInfoService.getCommitMessage());
        status.put("projectCount", projectRepository.count());
        status.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        return status;
    }
}
