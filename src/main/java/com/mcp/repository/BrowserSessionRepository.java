package com.mcp.repository;

import com.mcp.entity.BrowserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrowserSessionRepository extends JpaRepository<BrowserSession, Long> {
    Optional<BrowserSession> findBySessionId(String sessionId);
    List<BrowserSession> findByProjectId(Long projectId);
    List<BrowserSession> findByStatus(String status);
}
