package com.mcp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.SystemStatusDTO;
import com.mcp.service.SystemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Pattern;
import io.github.overrridee.annotation.ResponseEnvelope;

@RestController
@RequestMapping("/api/system")
@Validated
@ResponseEnvelope
@Tag(name = "System", description = "System health, git info, and AGENT configuration diagnostics.")
public class SystemController {

	private final SystemService systemService;

	public SystemController(SystemService systemService) {
		this.systemService = systemService;
	}

	/**
	 * {@code GET /api/system} : Read system state. Variant selected via
	 * {@code X-View} header.
	 *
	 * @param view {@code health} | {@code info} | {@code agent-status}
	 * @return State map appropriate to the view
	 */
	@GetMapping
	@Operation(summary = "system-status", description = "Read system state via the X-View request header:\n\n" +
			"• X-View: health (default) — Returns {status: UP|DEGRADED, database: connected|disconnected}. " +
			"Checks DB connectivity. Use before any project operations to verify the server is ready.\n\n" +
			"• X-View: info — Returns {commit: string, branch: string, available: boolean}. " +
			"Reports the server's current git commit hash, active branch, and whether git is accessible.\n\n" +
			"• X-View: agent-status — Returns {baseUrl, model, timeoutSeconds, maxTokens, reachable: boolean}. " +
			"Shows the active Ollama/AGENT configuration and pings the endpoint to confirm it is reachable " +
			"before dispatching AGENT action requests.", responses = {
					@ApiResponse(responseCode = "200", description = "System state map returned"),
					@ApiResponse(responseCode = "400", description = "Unknown X-View value")
			})
	public SystemStatusDTO getSystemStatus(
			@Parameter(description = "View variant: 'health' (default) | 'info' | 'agent-status'", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"health", "info", "agent-status"})) 
			@RequestHeader(value = "X-View", required = false, defaultValue = "health") 
			@Pattern(regexp = "^(health|info|agent-status)$", message = "Invalid X-View value") String view) {
		return systemService.getSystemStatus(view);
	}
}
