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

	// Fix P: Replace unbounded ConcurrentHashMap with Caffeine caches.
	// 1-hour TTL (expireAfterAccess) + 1000 max sessions prevents memory leaks
	// in long-running deployments. Sessions are ephemeral — lost on restart is
	// acceptable.
	private final Cache<String, Set<String>> sessionFiles = Caffeine.newBuilder()
			.expireAfterAccess(1, TimeUnit.HOURS)
			.maximumSize(1000)
			.build();

	private final Cache<String, Map<String, String>> sessionFileChecksums = Caffeine.newBuilder()
			.expireAfterAccess(1, TimeUnit.HOURS)
			.maximumSize(1000)
			.build();

	public void recordAccess(String sessionId, String filePath, String checksum) {
		if (sessionId == null)
			return;
		sessionFiles.asMap().computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(filePath);
		if (checksum != null) {
			sessionFileChecksums.asMap().computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(filePath,
					checksum);
		}
	}

	public boolean hasBeenViewed(String sessionId, String filePath) {
		if (sessionId == null)
			return false;
		Set<String> files = sessionFiles.getIfPresent(sessionId);
		return files != null && files.contains(filePath);
	}

	public String getLastChecksum(String sessionId, String filePath) {
		if (sessionId == null)
			return null;
		Map<String, String> checksums = sessionFileChecksums.getIfPresent(sessionId);
		return checksums != null ? checksums.get(filePath) : null;
	}

	public void clearSession(String sessionId) {
		sessionFiles.invalidate(sessionId);
		sessionFileChecksums.invalidate(sessionId);
	}

	public Set<String> getSessionFiles(String sessionId) {
		Set<String> files = sessionFiles.getIfPresent(sessionId);
		return files != null ? files : Collections.emptySet();
	}

}
