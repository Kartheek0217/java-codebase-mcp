package com.mcp.controller;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "System health, git info, and AGENT configuration diagnostics.")
public class SystemController {

	private final com.mcp.service.SystemService systemService;

	public SystemController(com.mcp.service.SystemService systemService) {
		this.systemService = systemService;
	}

	/**
	 * {@code GET /api/system} : Read system state. Variant selected via {@code X-View} header.
	 *
	 * @param view {@code health} | {@code info} | {@code agent-status}
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
			"• X-View: agent-status — Returns {baseUrl, model, timeoutSeconds, maxTokens, reachable: boolean}. " +
				"Shows the active Ollama/AGENT configuration and pings the endpoint to confirm it is reachable " +
				"before dispatching AGENT action requests.",
		responses = {
			@ApiResponse(responseCode = "200", description = "System state map returned"),
			@ApiResponse(responseCode = "400", description = "Unknown X-View value")
		}
	)
	public com.mcp.dto.SystemStatusDTO getSystemStatus(
			@Parameter(description = "View variant: 'health' (default) | 'info' | 'agent-status'")
			@RequestHeader(value = "X-View", required = false, defaultValue = "health") String view) {
		return switch (view.toLowerCase()) {
			case "health" -> systemService.getHealthStatus();
			case "info" -> systemService.getInfoStatus();
			case "agent-status" -> systemService.getAgentStatus();
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-View value '" + view + "'. Allowed: health, info, agent-status");
		};
	}
}
