package com.mcp.repository;

import com.mcp.entity.ProjectRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ProjectRuleRepository extends JpaRepository<ProjectRule, Long> {
    @EntityGraph(attributePaths = {"project"})
    List<ProjectRule> findByProjectId(Long projectId);
    List<ProjectRule> findByProjectIdAndCategory(Long projectId, String category);

    @Modifying
    @Transactional
    void deleteByProjectId(Long projectId);
}
