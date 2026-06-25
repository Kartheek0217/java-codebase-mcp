package com.mcp.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.SessionDTO;
import com.mcp.repository.ProjectRepository;
import com.mcp.service.ContextMemoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "Sessions", description = "Agentic session management for IDE agent workflows.")
public class SessionController {

	private final ProjectRepository projectRepository;
	private final ContextMemoryService contextMemoryService;

	private final Map<String, Session> sessionStore;

	public SessionController(ProjectRepository projectRepository, ContextMemoryService contextMemoryService) {
		this.projectRepository = projectRepository;
		this.contextMemoryService = contextMemoryService;
		this.sessionStore = Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
				return size() > 1000;
			}
		});
	}

	/**
	 * @implNote Cleans sessions older than 1 hour from store and memory
	 * @return void
	 * @author JCB
	 */
	@Scheduled(fixedDelay = 3_600_000)
	public void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();
		List<String> expired;
		synchronized (sessionStore) {
			expired = sessionStore.entrySet().stream()
				.filter(e -> now - e.getValue().createdAt() > 3_600_000L)
				.map(e -> e.getKey()).toList();
			expired.forEach(sessionStore::remove);
		}
		expired.forEach(contextMemoryService::clearSession);
	}

	/**
	 * {@code POST /api/mcp/sessions} : Start AI agent session for project.
	 * 
	 * @implNote Stores Session with new UUID
	 * @param projectId
	 * @return Map with sessionId
	 * @author JCB
	 */
	@PostMapping("/sessions")
	@Operation(
		summary = "start-session",
		description = "Start a new AI agent session bound to a project. Sessions track file access history " +
			"and provide context continuity across multiple MCP tool calls. " +
			"Query param: projectId (Long, required). " +
			"Returns {sessionId: string}. Sessions expire after 1 hour of inactivity.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Session created, returns {sessionId}"),
			@ApiResponse(responseCode = "404", description = "Project not found")
		}
	)
	public Map<String, String> startSession(@RequestParam Long projectId) {
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		String sessionId = UUID.randomUUID().toString();
		sessionStore.put(sessionId, new Session(sessionId, projectId));
		return Map.of("sessionId", sessionId);
	}

	/**
	 * {@code GET /api/mcp/sessions/{sessionId}} : Retrieve session metadata.
	 * 
	 * @implNote Retrieves existing Session from store
	 * @param sessionId
	 * @return SessionDTO
	 * @author JCB
	 */
	@GetMapping("/sessions/{sessionId}")
	@Operation(
		summary = "get-session",
		description = "Retrieve metadata for an active agent session. " +
			"Path param: sessionId (string). " +
			"Returns SessionDTO {sessionId, projectId, createdAt, files: []}. " +
			"Use this to verify a session is still active before making context-dependent tool calls.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Session metadata returned"),
			@ApiResponse(responseCode = "404", description = "Session not found or expired")
		}
	)
	public SessionDTO getSession(@PathVariable String sessionId) {
		Session session = sessionStore.get(sessionId);
		if (session == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or expired: " + sessionId);
		return new SessionDTO(session.sessionId(), session.projectId(), session.createdAt(), Collections.emptyList());
	}

	/**
	 * Compact record replacing the previous 13-line boilerplate inner class.
	 * {@code createdAt} is epoch millis captured at construction time.
	 */
	private record Session(String sessionId, Long projectId, long createdAt) {
		Session(String sessionId, Long projectId) {
			this(sessionId, projectId, System.currentTimeMillis());
		}
	}
}
