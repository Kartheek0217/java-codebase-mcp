package com.mcp.repository;

import com.mcp.entity.ProjectTask;
import com.mcp.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"steps"})
    List<ProjectTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM ProjectTask t WHERE t.id = :id")
    java.util.Optional<ProjectTask> findByIdForUpdate(Long id);
    
    List<ProjectTask> findByProjectIdAndStatus(Long projectId, TaskStatus status);
    void deleteByProjectId(Long projectId);
}
