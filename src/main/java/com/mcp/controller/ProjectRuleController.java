package com.mcp.controller;

import com.mcp.dto.RuleDTO;
import com.mcp.service.ProjectRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/rules")
@Tag(name = "Project Rules", description = "Endpoints for managing project-specific rules and constraints.")
public class ProjectRuleController {

    private final ProjectRuleService ruleService;

    public ProjectRuleController(ProjectRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    @Operation(summary = "Get all rules for project")
    public List<RuleDTO> getRules(@RequestParam Long projectId) {
        return ruleService.getRulesByProject(projectId);
    }

    @PostMapping
    @Operation(summary = "Create a new rule")
    public RuleDTO createRule(@RequestBody RuleDTO ruleDTO) {
        return ruleService.createRule(ruleDTO);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a rule")
    public RuleDTO updateRule(@PathVariable Long id, @RequestBody RuleDTO ruleDTO) {
        return ruleService.updateRule(id, ruleDTO);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a rule")
    public Map<String, String> deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return Map.of("status", "success");
    }

    @DeleteMapping
    @Operation(summary = "Delete all rules for project")
    public Map<String, String> deleteRules(@RequestParam Long projectId) {
        ruleService.deleteRulesByProject(projectId);
        return Map.of("status", "success");
    }
}
