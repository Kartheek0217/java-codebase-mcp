package com.mcp.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.entity.Skill;
import com.mcp.repository.SkillRepository;
import com.mcp.service.SkillService;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "Skills", description = "Agentic skill management for IDE agent workflows.")
public class SkillController {

	private final SkillService skillService;
	private final SkillRepository skillRepository;

	public SkillController(SkillService skillService, SkillRepository skillRepository) {
		this.skillService = skillService;
		this.skillRepository = skillRepository;
	}

	/**
	 * {@code GET /api/mcp/skills} : List global and project skills.
	 * 
	 * @implNote Combines global and project-specific skills from repository
	 * @param projectId
	 * @return List of Skill
	 * @author JCB
	 */
	@GetMapping("/skills")
	@Operation(
		summary = "get-skills",
		description = "Retrieve available skills for an agent. " +
			"Query param: projectId (Long, optional). " +
			"If projectId is omitted, returns only globally registered built-in skills. " +
			"If projectId is provided, returns global skills plus project-specific learned skills. " +
			"Each Skill has: {id, name, description, content (markdown instructions), project (null if global), source}.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Skill list returned")
		}
	)
	public List<Skill> getSkills(@RequestParam(required = false) Long projectId) {
		List<Skill> globalSkills = skillRepository.findByProjectIdIsNull();
		if (projectId == null) return globalSkills;
		List<Skill> projectSkills = skillRepository.findByProjectId(projectId);
		List<Skill> allSkills = new java.util.ArrayList<>(globalSkills);
		allSkills.addAll(projectSkills);
		return allSkills;
	}

	@PostMapping("/skills/learn-url")
	@Operation(summary = "learn_skill_from_url", description = "Fetch and learn a skill from a URL or built-in path. Query params: projectId (required), url (required).")
	public Object learnSkillFromUrl(
			@RequestParam Long projectId,
			@RequestParam String url) throws IOException {
		if (url == null || url.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'url' is required");
		skillService.learnFromUrl(projectId, url);
		return Map.of("status", "success", "message", "Skill learned from: " + url);
	}

	@PostMapping("/skills/learn-file")
	@Operation(summary = "learn_skill_from_file", description = "Learn a skill from a local file path. Query params: projectId (required), filePath (required).")
	public Object learnSkillFromFile(
			@RequestParam Long projectId,
			@RequestParam String filePath) throws IOException {
		if (filePath == null || filePath.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'filePath' is required");
		skillService.learnFromFile(projectId, filePath);
		return Map.of("status", "success", "message", "Skill learned from file: " + filePath);
	}

	@DeleteMapping("/skills")
	@Operation(summary = "clear_skills", description = "Remove all project-specific learned skills. Query param: projectId (required).")
	public Object clearSkills(@RequestParam Long projectId) {
		skillService.deleteSkillsByProject(projectId);
		return Map.of("status", "success", "message", "All project skills cleared for projectId=" + projectId);
	}
}
