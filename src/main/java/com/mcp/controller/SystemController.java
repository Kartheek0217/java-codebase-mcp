package com.mcp.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.properties.OllamaProperties;
import com.mcp.repository.ProjectRepository;
import com.mcp.service.GitInfoService;
import com.mcp.service.OllamaClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System health, git info, and LLM configuration diagnostics.")
public class SystemController {

	private final ProjectRepository projectRepository;
	private final GitInfoService gitInfoService;
	private final OllamaClient ollamaClient;
	private final OllamaProperties ollamaProperties;

	public SystemController(ProjectRepository projectRepository,
			GitInfoService gitInfoService,
			OllamaClient ollamaClient,
			OllamaProperties ollamaProperties) {
		this.projectRepository = projectRepository;
		this.gitInfoService = gitInfoService;
		this.ollamaClient = ollamaClient;
		this.ollamaProperties = ollamaProperties;
	}

	/**
	 * {@code GET /api/system} : Read system state. Variant selected via {@code X-View} header.
	 *
	 * @param view {@code health} | {@code info} | {@code llm-status}
	 * @return State map appropriate to the view
	 */
	@GetMapping
	@Operation(
		summary = "system-status",
		description = "Read system state via the X-View request header:\n\n" +
			"• X-View: health (default) — Returns {status: UP|DEGRADED, database: connected|disconnected}. " +
				"Checks DB connectivity. Use before any project operations to verify the server is ready.\n\n" +
			"• X-View: info — Returns {commit: string, branch: string, available: boolean}. " +
				"Reports the server's current git commit hash, active branch, and whether git is accessible.\n\n" +
			"• X-View: llm-status — Returns {baseUrl, model, timeoutSeconds, maxTokens, reachable: boolean}. " +
				"Shows the active Ollama/LLM configuration and pings the endpoint to confirm it is reachable " +
				"before dispatching LLM action requests.",
		responses = {
			@ApiResponse(responseCode = "200", description = "System state map returned"),
			@ApiResponse(responseCode = "400", description = "Unknown X-View value")
		}
	)
	public Map<String, Object> getSystemStatus(
			@Parameter(description = "View variant: 'health' (default) | 'info' | 'llm-status'")
			@RequestHeader(value = "X-View", required = false, defaultValue = "health") String view) {
		return switch (view.toLowerCase()) {
			case "health" -> {
				Map<String, Object> health = new HashMap<>();
				health.put("status", "UP");
				try {
					projectRepository.count();
					health.put("database", "connected");
				} catch (Exception e) {
					health.put("status", "DEGRADED");
					health.put("database", "disconnected");
				}
				yield health;
			}
			case "info" -> {
				Map<String, Object> info = new HashMap<>();
				info.put("commit", gitInfoService.getCommitHash());
				info.put("branch", gitInfoService.getBranchName());
				info.put("available", gitInfoService.isGitAvailable());
				yield info;
			}
			case "llm-status" -> {
				Map<String, Object> status = new HashMap<>();
				status.put("baseUrl", ollamaProperties.getBaseUrl());
				status.put("model", ollamaProperties.getModel());
				status.put("timeoutSeconds", ollamaProperties.getTimeoutSeconds());
				status.put("maxTokens", ollamaProperties.getMaxTokens());
				status.put("reachable", ollamaClient.isReachable());
				yield status;
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-View value '" + view + "'. Allowed: health, info, llm-status");
		};
	}
}
