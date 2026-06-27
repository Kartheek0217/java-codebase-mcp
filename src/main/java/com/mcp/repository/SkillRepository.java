package com.mcp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mcp.entity.Skill;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
	List<Skill> findByProjectId(Long projectId);

	Optional<Skill> findByProjectIdAndName(Long projectId, String name);

	List<Skill> findByProjectIdIsNull();

	Optional<Skill> findByProjectIdIsNullAndName(String name);

	@Modifying
	@Transactional
	void deleteByProjectId(Long projectId);
}
