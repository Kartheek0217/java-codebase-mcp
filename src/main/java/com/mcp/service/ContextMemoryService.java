package com.mcp.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class ContextMemoryService {

	private record SessionState(Set<String> files, Map<String, String> checksums) {}

	// Fix P: Replace unbounded ConcurrentHashMap with Caffeine caches.
	// 1-hour TTL (expireAfterAccess) + 1000 max sessions prevents memory leaks
	// in long-running deployments. Sessions are ephemeral — lost on restart is
	// acceptable.
	private final Cache<String, SessionState> sessions = Caffeine.newBuilder()
			.expireAfterAccess(1, TimeUnit.HOURS)
			.maximumSize(1000)
			.build();

	public void recordAccess(String sessionId, String filePath, String checksum) {
		if (sessionId == null || filePath == null)
			return;
		SessionState state = sessions.get(sessionId, k -> new SessionState(ConcurrentHashMap.newKeySet(), new ConcurrentHashMap<>()));
		state.files().add(filePath);
		if (checksum != null) {
			state.checksums().put(filePath, checksum);
		}
	}

	public boolean hasBeenViewed(String sessionId, String filePath) {
		if (sessionId == null || filePath == null)
			return false;
		SessionState state = sessions.getIfPresent(sessionId);
		return state != null && state.files().contains(filePath);
	}

	public String getLastChecksum(String sessionId, String filePath) {
		if (sessionId == null || filePath == null)
			return null;
		SessionState state = sessions.getIfPresent(sessionId);
		return state != null ? state.checksums().get(filePath) : null;
	}

	public void clearSession(String sessionId) {
		if (sessionId == null) return;
		sessions.invalidate(sessionId);
	}

	public Set<String> getSessionFiles(String sessionId) {
		if (sessionId == null) return Collections.emptySet();
		SessionState state = sessions.getIfPresent(sessionId);
		return state != null ? Set.copyOf(state.files()) : Collections.emptySet();
	}

}
