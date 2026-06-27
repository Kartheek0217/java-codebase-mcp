package com.mcp.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.SessionDTO;
import com.mcp.service.SessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.mcp.dto.StartSessionResponse;
import io.github.overrridee.annotation.ResponseEnvelope;

@RestController
@RequestMapping("/api/mcp")
@Validated
@Tag(name = "Sessions", description = "Agentic session management for IDE agent workflows.")
public class SessionController {

	private final SessionService sessionService;

	public SessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	/**
	 * {@code POST /api/mcp/sessions} : Start AI agent session for project.
	 * 
	 * @param projectId
	 * @return StartSessionResponse
	 */
	@PostMapping("/sessions")
	@ResponseEnvelope
	@Operation(summary = "start-session", description = "Start a new AI agent session bound to a project. Sessions track file access history "
			+
			"and provide context continuity across multiple MCP tool calls. " +
			"Header param: projectId (Long, required). " +
			"Returns {sessionId: string}. Sessions expire after 1 hour of inactivity.", responses = {
					@ApiResponse(responseCode = "200", description = "Session created, returns {sessionId}"),
					@ApiResponse(responseCode = "404", description = "Project not found")
			})
	public StartSessionResponse startSession(
			@RequestHeader(required = true) @NotNull Long projectId) {
		Map<String, String> result = sessionService.startSession(projectId);
		String sessionId = result.get("sessionId");
		if (sessionId == null) {
			throw new IllegalStateException("Session ID not returned by service");
		}
		return new StartSessionResponse(sessionId);
	}

	/**
	 * {@code GET /api/mcp/sessions/{sessionId}} : Retrieve session metadata.
	 * 
	 * @param sessionId
	 * @return SessionDTO
	 */
	@GetMapping("/sessions/{sessionId}")
	@ResponseEnvelope
	@Operation(summary = "get-session", description = "Retrieve metadata for an active agent session. " +
			"Path param: sessionId (string). " +
			"Returns SessionDTO {sessionId, projectId, createdAt, files: []}. " +
			"Use this to verify a session is still active before making context-dependent tool calls.", responses = {
					@ApiResponse(responseCode = "200", description = "Session metadata returned"),
					@ApiResponse(responseCode = "404", description = "Session not found or expired")
			})
	public SessionDTO getSession(@PathVariable @NotBlank String sessionId) {
		return sessionService.getSession(sessionId);
	}
}
