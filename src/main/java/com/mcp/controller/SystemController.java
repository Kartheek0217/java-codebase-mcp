package com.mcp.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.repository.ProjectRepository;
import com.mcp.service.GitInfoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Endpoints for system health and global information.")
public class SystemController {

	private final ProjectRepository projectRepository;
	private final GitInfoService gitInfoService;

	public SystemController(ProjectRepository projectRepository, GitInfoService gitInfoService) {
		this.projectRepository = projectRepository;
		this.gitInfoService = gitInfoService;
	}

	@GetMapping("/health")
	@Operation(summary = "get-health", description = "Check system health")
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
		return health;
	}

	@GetMapping("/info")
	@Operation(summary = "get-info", description = "Get system info")
	public Map<String, String> getInfo() {
		Map<String, String> info = new HashMap<>();
		info.put("commit", gitInfoService.getCommitHash());
		info.put("branch", gitInfoService.getBranchName());
		info.put("available", String.valueOf(gitInfoService.isGitAvailable()));
		return info;
	}
}
