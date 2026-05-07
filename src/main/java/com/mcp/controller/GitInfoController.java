package com.mcp.controller;

import com.mcp.service.GitInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git-info")
@Tag(name = "Git Info", description = "Endpoints for project-level Git operations (status, stage, commit) and global system Git info.")
public class GitInfoController {

    private final GitInfoService gitInfoService;

    public GitInfoController(GitInfoService gitInfoService) {
        this.gitInfoService = gitInfoService;
    }

    @GetMapping
    @Operation(
        summary = "Get global server Git info", 
        description = "Returns Git metadata about the MCP server's own source code (commit, branch, message).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Global Git info retrieved successfully")
        }
    )
    public Map<String, String> getGitInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("commit", gitInfoService.getCommitHash());
        info.put("branch", gitInfoService.getBranchName());
        info.put("message", gitInfoService.getCommitMessage());
        info.put("available", String.valueOf(gitInfoService.isGitAvailable()));
        return info;
    }

    @GetMapping("/projects/{projectId}/status")
    @Operation(
        summary = "Get project Git status", 
        description = "Returns a detailed status of the project's Git repository, including modified, added, removed, and untracked files.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Project Git status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Project not found or Git repository missing")
        }
    )
    public Map<String, Object> getProjectStatus(
            @Parameter(description = "ID of the project") @PathVariable Long projectId) {
        return gitInfoService.getProjectStatus(projectId);
    }

    @PostMapping("/projects/{projectId}/stage")
    @Operation(
        summary = "Stage project changes (git add)", 
        description = "Adds specified files or glob patterns to the Git index for a project.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Files staged successfully")
        }
    )
    public Map<String, String> stageFiles(
            @Parameter(description = "ID of the project") @PathVariable Long projectId, 
            @Parameter(description = "List of file paths or patterns to stage") @RequestBody List<String> patterns) {
        gitInfoService.stageFiles(projectId, patterns);
        return Map.of("message", "Files staged successfully");
    }

    @PostMapping("/projects/{projectId}/discard")
    @Operation(
        summary = "Discard local changes (git checkout)", 
        description = "Reverts local modifications in the specified files to their last committed state.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Changes discarded successfully")
        }
    )
    public Map<String, String> discardChanges(
            @Parameter(description = "ID of the project") @PathVariable Long projectId, 
            @Parameter(description = "List of file paths or patterns to revert") @RequestBody List<String> patterns) {
        gitInfoService.discardChanges(projectId, patterns);
        return Map.of("message", "Changes discarded successfully");
    }

    @PostMapping("/projects/{projectId}/commit")
    @Operation(
        summary = "Commit staged changes", 
        description = "Creates a new Git commit for the specified project with all currently staged changes.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Changes committed successfully")
        }
    )
    public Map<String, String> commit(
            @Parameter(description = "ID of the project") @PathVariable Long projectId, 
            @Parameter(description = "Commit message explaining the changes") @RequestParam String message) {
        String hash = gitInfoService.commit(projectId, message);
        return Map.of("message", "Committed successfully", "commitHash", hash);
    }
}
