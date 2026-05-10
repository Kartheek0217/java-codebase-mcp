package com.mcp.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mcp.dto.RuleDTO;
import com.mcp.entity.Project;
import com.mcp.entity.ProjectRule;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.ProjectRuleRepository;

@Service
@Transactional
public class ProjectRuleService {

	private final ProjectRuleRepository ruleRepository;
	private final ProjectRepository projectRepository;

	public ProjectRuleService(ProjectRuleRepository ruleRepository, ProjectRepository projectRepository) {
		this.ruleRepository = ruleRepository;
		this.projectRepository = projectRepository;
	}

	@Transactional(readOnly = true)
	public List<RuleDTO> getRulesByProject(Long projectId) {
		return ruleRepository.findByProjectId(projectId).stream().map(this::toDTO).toList();
	}

	@Transactional
	public RuleDTO createRule(RuleDTO ruleDTO) {
		Project project = projectRepository.findById(ruleDTO.projectId())
				.orElseThrow(() -> new IllegalArgumentException("Project not found: " + ruleDTO.projectId()));

		ProjectRule rule = new ProjectRule();
		rule.setProject(project);
		rule.setName(ruleDTO.name());
		rule.setRuleValue(ruleDTO.value());
		rule.setCategory(ruleDTO.category());
		rule.setDescription(ruleDTO.description());

		return toDTO(ruleRepository.save(rule));
	}

	@Transactional
	public RuleDTO updateRule(Long id, RuleDTO ruleDTO) {
		ProjectRule rule = ruleRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));

		rule.setName(ruleDTO.name());
		rule.setRuleValue(ruleDTO.value());
		rule.setCategory(ruleDTO.category());
		rule.setDescription(ruleDTO.description());

		return toDTO(ruleRepository.save(rule));
	}

	@Transactional
	public void deleteRule(Long id) {
		ruleRepository.deleteById(id);
	}

	@Transactional
	public void deleteRulesByProject(Long projectId) {
		ruleRepository.deleteByProjectId(projectId);
	}

	private RuleDTO toDTO(ProjectRule rule) {
		return new RuleDTO(rule.getId(), rule.getProject().getId(), rule.getName(), rule.getRuleValue(),
				rule.getCategory(), rule.getDescription());
	}
}
