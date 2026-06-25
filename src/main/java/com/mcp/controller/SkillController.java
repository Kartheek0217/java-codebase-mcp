package com.mcp.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.entity.Skill;
import com.mcp.repository.SkillRepository;
import com.mcp.service.SkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

	/**
	 * {@code POST /api/mcp/skills} : Execute skill operation.
	 * 
	 * @implNote Routes learn-url/learn-file/clear to skillService
	 * @param op, projectId, url, filePath
	 * @return Object
	 * @exception IOException
	 * @author JCB
	 */
	@PostMapping("/skills")
	@Operation(
		summary = "skill-op",
		description = "Learn a new skill from a URL or local file, or clear all project skills via X-Op:\n\n" +
			"• X-Op: learn-url — Fetch and learn a skill from a URL or built-in path. " +
				"Query params: projectId (required), url (required, e.g. https://example.com/SKILL.md). " +
				"The skill's name and description are parsed from the SKILL.md frontmatter.\n\n" +
			"• X-Op: learn-file — Learn a skill from a local file path on the server. " +
				"Query params: projectId (required), filePath (required, absolute or relative path to SKILL.md).\n\n" +
			"• X-Op: clear — Remove all project-specific learned skills. " +
				"Query param: projectId (required). Global built-in skills are not affected.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Skill learned successfully"),
			@ApiResponse(responseCode = "400", description = "Missing required param or unknown X-Op"),
			@ApiResponse(responseCode = "404", description = "Project not found")
		}
	)
	public Object skillOp(
			@Parameter(description = "Operation: learn-url | learn-file | clear")
			@RequestHeader(value = "X-Op") String op,
			@RequestParam Long projectId,
			@RequestParam(required = false) String url,
			@RequestParam(required = false) String filePath) throws IOException {
		return switch (op.toLowerCase()) {
			case "learn-url" -> {
				if (url == null || url.isBlank())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'url' is required for op=learn-url");
				skillService.learnFromUrl(projectId, url);
				yield Map.of("status", "success", "message", "Skill learned from: " + url);
			}
			case "learn-file" -> {
				if (filePath == null || filePath.isBlank())
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'filePath' is required for op=learn-file");
				skillService.learnFromFile(projectId, filePath);
				yield Map.of("status", "success", "message", "Skill learned from file: " + filePath);
			}
			case "clear" -> {
				skillService.deleteSkillsByProject(projectId);
				yield Map.of("status", "success", "message", "All project skills cleared for projectId=" + projectId);
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unknown X-Op value '" + op + "'. Allowed: learn-url, learn-file, clear");
		};
	}
}
