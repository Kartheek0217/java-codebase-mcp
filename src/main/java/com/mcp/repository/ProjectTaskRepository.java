package com.mcp.repository;

import com.mcp.entity.ProjectTask;
import com.mcp.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    @EntityGraph(attributePaths = {"steps"})
    List<ProjectTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ProjectTask t WHERE t.id = :id")
    Optional<ProjectTask> findByIdForUpdate(Long id);
    
    List<ProjectTask> findByProjectIdAndStatus(Long projectId, TaskStatus status);
    void deleteByProjectId(Long projectId);
}
