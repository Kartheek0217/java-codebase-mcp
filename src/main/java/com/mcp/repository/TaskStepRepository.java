package com.mcp.repository;

import com.mcp.entity.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {
    List<TaskStep> findByTaskIdOrderByStepNumber(Long taskId);

    @Modifying
    @Transactional
    void deleteByTaskId(Long taskId);
}
