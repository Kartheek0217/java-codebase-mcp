package com.mcp.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.mcp.entity.AgentSubTask;

public interface AgentSubTaskRepository extends JpaRepository<AgentSubTask, Long> {
    List<AgentSubTask> findByMainTaskId(Long mainTaskId);
}
