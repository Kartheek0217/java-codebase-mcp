package com.mcp.repository;

import com.mcp.entity.ProjectRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ProjectRuleRepository extends JpaRepository<ProjectRule, Long> {
    List<ProjectRule> findByProjectId(Long projectId);
    List<ProjectRule> findByProjectIdAndCategory(Long projectId, String category);

    @Modifying
    @Transactional
    void deleteByProjectId(Long projectId);
}
