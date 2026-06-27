package com.mcp.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.mcp.entity.Skill;
import com.mcp.service.SkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.mcp.dto.SkillOperationResponse;
import io.github.overrridee.annotation.ResponseEnvelope;

@RestController
@RequestMapping("/api/mcp")
@Validated
@ResponseEnvelope
@Tag(name = "Skills", description = "Agentic skill management for IDE agent workflows.")
public class SkillController {

	private final SkillService skillService;

	public SkillController(SkillService skillService) {
		this.skillService = skillService;
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<SkillOperationResponse> handleIOException(IOException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new SkillOperationResponse("ERROR", ex.getMessage()));
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
		return skillService.getSkills(projectId);
	}

	@PostMapping("/skills/learn-url")
	@Operation(summary = "learn_skill_from_url", description = "Fetch and learn a skill from a URL or built-in path. Query params: projectId (required), url (required).")
	public SkillOperationResponse learnSkillFromUrl(
			@RequestParam(value = "projectId", required = true) @NotNull Long projectId,
			@RequestParam(value = "url", required = true) @NotBlank String url) throws IOException {
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must use http or https");
		}
		Map<String, String> res = skillService.learnSkillFromUrl(projectId, url);
		return new SkillOperationResponse(res.get("status"), res.get("message"));
	}

	@PostMapping("/skills/learn-file")
	@Operation(summary = "learn_skill_from_file", description = "Learn a skill from a local file path. Query params: projectId (required), filePath (required).")
	public SkillOperationResponse learnSkillFromFile(
			@RequestParam(value = "projectId", required = true) @NotNull Long projectId,
			@RequestParam(value = "filePath", required = true) @NotBlank String filePath) throws IOException {
		Path path = Paths.get(filePath).normalize();
		if (path.toString().contains("..") || path.isAbsolute()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
		}
		Map<String, String> res = skillService.learnSkillFromFile(projectId, filePath);
		return new SkillOperationResponse(res.get("status"), res.get("message"));
	}

	@DeleteMapping("/skills")
	@Operation(summary = "clear_skills", description = "Remove all project-specific learned skills. Query param: projectId (required).")
	public SkillOperationResponse clearSkills(
			@RequestParam(value = "projectId", required = true) @NotNull Long projectId) {
		Map<String, String> res = skillService.clearSkills(projectId);
		return new SkillOperationResponse(res.get("status"), res.get("message"));
	}
}
