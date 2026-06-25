package com.mcp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mcp.entity.AgentTask;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
    List<AgentTask> findByProjectId(Long projectId);
}
