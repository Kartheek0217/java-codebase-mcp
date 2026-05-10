package com.mcp.repository;

import com.mcp.entity.BrowserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrowserSessionRepository extends JpaRepository<BrowserSession, Long> {
    Optional<BrowserSession> findBySessionId(String sessionId);
    List<BrowserSession> findByProjectId(Long projectId);
    List<BrowserSession> findByStatus(String status);
}
