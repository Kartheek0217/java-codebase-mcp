package com.mcp.repository;

import com.mcp.entity.ProjectTask;
import com.mcp.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    List<ProjectTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ProjectTask> findByProjectIdAndStatus(Long projectId, TaskStatus status);
    void deleteByProjectId(Long projectId);
}
