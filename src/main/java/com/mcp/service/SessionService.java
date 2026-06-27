package com.mcp.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.SessionDTO;
import com.mcp.repository.ProjectRepository;

@Service
public class SessionService {

	private final ProjectRepository projectRepository;
	private final ContextMemoryService contextMemoryService;
	private final Map<String, Session> sessionStore;

	public SessionService(ProjectRepository projectRepository, ContextMemoryService contextMemoryService) {
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
	 * Cleans sessions older than 1 hour from store and memory.
	 */
	@Scheduled(fixedDelay = 3_600_000)
	public void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();
		synchronized (sessionStore) {
			List<String> expired = sessionStore.entrySet().stream()
					.filter(e -> now - e.getValue().createdAt() > 3_600_000L)
					.map(e -> e.getKey()).toList();
			expired.forEach(sessionStore::remove);
			expired.forEach(contextMemoryService::clearSession);
		}
	}

	/**
	 * Start AI agent session for project.
	 */
	public Map<String, String> startSession(Long projectId) {
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		String sessionId = UUID.randomUUID().toString();
		sessionStore.put(sessionId, new Session(sessionId, projectId));
		return Map.of("sessionId", sessionId);
	}

	/**
	 * Retrieve session metadata.
	 */
	public SessionDTO getSession(String sessionId) {
		Session session = sessionStore.get(sessionId);
		if (session == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found or expired: " + sessionId);
		return new SessionDTO(session.sessionId(), session.projectId(), session.createdAt(), Collections.emptyList());
	}

	private record Session(String sessionId, Long projectId, long createdAt) {
		Session(String sessionId, Long projectId) {
			this(sessionId, projectId, System.currentTimeMillis());
		}
	}
}
