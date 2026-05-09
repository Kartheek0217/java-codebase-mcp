package com.mcp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mcp.entity.Skill;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {
	List<Skill> findByProjectId(Long projectId);

	Optional<Skill> findByProjectIdAndName(Long projectId, String name);

	void deleteByProjectId(Long projectId);
}
