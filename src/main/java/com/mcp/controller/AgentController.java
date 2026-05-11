package com.mcp.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.dto.CreateTaskRequest;
import com.mcp.dto.RuleDTO;
import com.mcp.dto.SessionDTO;
import com.mcp.dto.TaskDTO;
import com.mcp.entity.Skill;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.service.ContextMemoryService;
import com.mcp.service.ProjectRuleService;
import com.mcp.service.SkillService;
import com.mcp.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent", description = "Endpoints for agentic task management, rules, and skills.")
public class AgentController {


	private final TaskService taskService;
	private final ProjectRuleService ruleService;
	private final SkillService skillService;
	private final SkillRepository skillRepository;
	private final ProjectRepository projectRepository;
	private final ContextMemoryService contextMemoryService;

	private final Map<String, Session> sessionStore = Collections
			.synchronizedMap(new LinkedHashMap<>(100, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
					return size() > 1000;
				}
			});

	private final ScheduledExecutorService sessionCleanup = Executors.newSingleThreadScheduledExecutor();

	public AgentController(TaskService taskService, ProjectRuleService ruleService, SkillService skillService,
			SkillRepository skillRepository, ProjectRepository projectRepository,
			ContextMemoryService contextMemoryService) {
		this.taskService = taskService;
		this.ruleService = ruleService;
		this.skillService = skillService;
		this.skillRepository = skillRepository;
		this.projectRepository = projectRepository;
		this.contextMemoryService = contextMemoryService;
	}


	@PostConstruct
	public void init() {
		sessionCleanup.scheduleWithFixedDelay(() -> {
			long now = System.currentTimeMillis();
			sessionStore.entrySet().removeIf(e -> {
				boolean expired = (now - e.getValue().createdAt) > 3600000L;
				if (expired) {
					contextMemoryService.clearSession(e.getKey());
				}
				return expired;
			});
		}, 1, 1, TimeUnit.HOURS);
	}

	// Sessions
	@PostMapping("/sessions")
	@Operation(summary = "start-session", description = "Start AI session")
	public Map<String, String> startSession(@RequestParam Long projectId) {
		projectRepository.findById(projectId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
		String sessionId = UUID.randomUUID().toString();
		sessionStore.put(sessionId, new Session(sessionId, projectId));
		return Map.of("sessionId", sessionId);
	}

	@GetMapping("/sessions/{sessionId}")
	@Operation(summary = "get-session", description = "Get session details")
	public SessionDTO getSession(@PathVariable String sessionId) {
		Session session = sessionStore.get(sessionId);
		if (session == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
		}
		return new SessionDTO(session.sessionId, session.projectId, session.createdAt, Collections.emptyList());
	}

	// Tasks
	@GetMapping("/tasks")
	@Operation(summary = "get-tasks", description = "Get all tasks for project")
	public List<TaskDTO> getTasks(@RequestParam Long projectId) {
		return taskService.getTasksByProject(projectId);
	}

	@PostMapping("/tasks")
	@Operation(summary = "crt-task", description = "Create task")
	public TaskDTO createTask(@RequestBody CreateTaskRequest request) {
		return taskService.createTask(request);
	}

	@PutMapping("/tasks/{id}")
	@Operation(summary = "upd-task", description = "Update task")
	public TaskDTO updateTask(@PathVariable Long id, @RequestBody TaskDTO taskDTO) {
		return taskService.updateTask(id, taskDTO);
	}

	@PutMapping("/tasks/{taskId}/steps/{stepId}")
	@Operation(summary = "upd-step-status", description = "Update task step status")
	public TaskDTO updateStepStatus(@PathVariable Long taskId, @PathVariable Long stepId, @RequestParam String status) {
		com.mcp.model.TaskStatus taskStatus = com.mcp.model.TaskStatus.valueOf(status.toUpperCase());
		return taskService.updateStepStatus(taskId, stepId, taskStatus);
	}

	@DeleteMapping("/tasks/{id}")
	@Operation(summary = "del-task", description = "Delete task")
	public void deleteTask(@PathVariable Long id) {
		taskService.deleteTask(id);
	}

	// Rules

	@GetMapping("/rules")
	@Operation(summary = "get-rules", description = "Get all rules for project")
	public List<RuleDTO> getRules(@RequestParam Long projectId) {
		return ruleService.getRulesByProject(projectId);
	}

	@PostMapping("/rules")
	@Operation(summary = "crt-rule", description = "Create rule")
	public RuleDTO createRule(@RequestBody RuleDTO ruleDTO) {
		return ruleService.createRule(ruleDTO);
	}

	@DeleteMapping("/rules/{id}")

	@Operation(summary = "del-rule", description = "Delete rule")
	public void deleteRule(@PathVariable Long id) {
		ruleService.deleteRule(id);
	}

	@DeleteMapping("/rules")
	@Operation(summary = "clear-rules", description = "Clear all rules for project")
	public void clearRules(@RequestParam Long projectId) {
		ruleService.deleteRulesByProject(projectId);
	}

	// Skills
	@GetMapping("/skills")
	@Operation(summary = "get-skills", description = "Get all skills for project")
	public List<Skill> getSkills(@RequestParam Long projectId) {
		return skillRepository.findByProjectId(projectId);
	}

	@DeleteMapping("/skills")
	@Operation(summary = "clear-skills", description = "Clear all skills for project")
	public void clearSkills(@RequestParam Long projectId) {
		skillService.deleteSkillsByProject(projectId);
	}

	@PostMapping("/skills/learn")
	@Operation(summary = "learn-skill", description = "Learn skill from URL")
	public Map<String, String> learnSkill(@RequestParam Long projectId, @RequestParam String url) {
		skillService.learnFromUrl(projectId, url);
		return Map.of("status", "success");
	}

	@PostMapping("/skills/learn-from-file")

	@Operation(summary = "learn-skill-file", description = "Learn skill from local file")
	public Map<String, String> learnSkillFromFile(
			@RequestParam Long projectId,
			@RequestParam String filePath) throws IOException {
		skillService.learnFromFile(projectId, filePath);
		return Map.of("status", "success", "message", "Skill learned from file: " + filePath);
	}

	private static class Session {
		final String sessionId;
		final Long projectId;
		final long createdAt;

		Session(String sessionId, Long projectId) {
			this.sessionId = sessionId;
			this.projectId = projectId;
			this.createdAt = System.currentTimeMillis();
		}
	}
}
