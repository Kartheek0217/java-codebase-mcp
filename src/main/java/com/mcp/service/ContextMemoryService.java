package com.mcp.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class ContextMemoryService {
	// Map<SessionId, Set<FilePath>>
	private final Map<String, Set<String>> sessionFiles = new ConcurrentHashMap<>();

	// Map<SessionId, Map<FilePath, String>>
	private final Map<String, Map<String, String>> sessionFileChecksums = new ConcurrentHashMap<>();

	public void recordAccess(String sessionId, String filePath, String checksum) {
		if (sessionId == null)
			return;
		sessionFiles.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(filePath);
		if (checksum != null) {
			sessionFileChecksums.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(filePath, checksum);
		}
	}

	public boolean hasBeenViewed(String sessionId, String filePath) {
		if (sessionId == null)
			return false;
		Set<String> files = sessionFiles.get(sessionId);
		return files != null && files.contains(filePath);
	}

	public String getLastChecksum(String sessionId, String filePath) {
		if (sessionId == null)
			return null;
		Map<String, String> checksums = sessionFileChecksums.get(sessionId);
		return checksums != null ? checksums.get(filePath) : null;
	}

	public void clearSession(String sessionId) {
		sessionFiles.remove(sessionId);
		sessionFileChecksums.remove(sessionId);
	}
}
