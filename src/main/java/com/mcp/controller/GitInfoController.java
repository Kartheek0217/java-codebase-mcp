package com.mcp.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.service.GitInfoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/git-info")
@Tag(name = "Git Info", description = "Endpoints for project-level Git operations (status, stage, commit) and global system Git info.")
public class GitInfoController {

	private final GitInfoService gitInfoService;

	public GitInfoController(GitInfoService gitInfoService) {
		this.gitInfoService = gitInfoService;
	}

	/**
	 * Retrieves global Git metadata for the MCP server itself.
	 *
	 * @return A map containing commit hash, branch name, and status
	 */
	@GetMapping
	@Operation(summary = "Get global server Git info", description = "Returns Git metadata about the MCP server's own source code (commit, branch, message).", responses = {
			@ApiResponse(responseCode = "200", description = "Global Git info retrieved successfully") })
	public Map<String, String> getGitInfo() {
		Map<String, String> info = new HashMap<>();
		info.put("commit", gitInfoService.getCommitHash());
		info.put("branch", gitInfoService.getBranchName());
		info.put("message", gitInfoService.getCommitMessage());
		info.put("available", String.valueOf(gitInfoService.isGitAvailable()));
		return info;
	}
}
