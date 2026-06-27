package com.mcp.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.dto.RuleDTO;
import com.mcp.entity.Project;
import com.mcp.entity.ProjectRule;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.ProjectRuleRepository;
import java.util.Map;

@Service
@Transactional
public class ProjectRuleService {

	private final ProjectRuleRepository ruleRepository;
	private final ProjectRepository projectRepository;
	private final ObjectMapper objectMapper;

	public ProjectRuleService(ProjectRuleRepository ruleRepository, ProjectRepository projectRepository, ObjectMapper objectMapper) {
		this.ruleRepository = ruleRepository;
		this.projectRepository = projectRepository;
		this.objectMapper = objectMapper;
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
	public RuleDTO createRule(Object body) {
		if (body == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body is required");
		Object actualBody = body;
		if (body instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) body;
			if (map.containsKey("body")) {
				actualBody = map.get("body");
			}
		}
		RuleDTO rule = convertBody(actualBody, RuleDTO.class);
		return createRule(rule);
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

	private <T> T convertBody(Object raw, Class<T> type) {
		if (raw == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		if (type.isInstance(raw)) return type.cast(raw);
		try {
			return objectMapper.convertValue(raw, type);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Invalid request body for type " + type.getSimpleName() + ": " + e.getMessage());
		}
	}
}
