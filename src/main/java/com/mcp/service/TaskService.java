package com.mcp.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.dto.CreateTaskRequest;
import com.mcp.dto.TaskDTO;
import com.mcp.dto.TaskStepDTO;
import com.mcp.entity.Project;
import com.mcp.entity.ProjectTask;
import com.mcp.entity.TaskStep;
import com.mcp.model.TaskStatus;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.ProjectTaskRepository;
import com.mcp.repository.TaskStepRepository;

@Service
@Transactional
public class TaskService {

	private final ProjectTaskRepository taskRepository;
	private final TaskStepRepository stepRepository;
	private final ProjectRepository projectRepository;
	private final ObjectMapper objectMapper;

	public TaskService(ProjectTaskRepository taskRepository, TaskStepRepository stepRepository,
			ProjectRepository projectRepository, ObjectMapper objectMapper) {
		this.taskRepository = taskRepository;
		this.stepRepository = stepRepository;
		this.projectRepository = projectRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<TaskDTO> getTasksByProject(Long projectId) {
		return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toDTO).toList();
	}

	@Transactional
	public TaskDTO createTask(CreateTaskRequest request) {
		Project project = projectRepository.findById(request.projectId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + request.projectId()));

		ProjectTask task = new ProjectTask();
		task.setProject(project);
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setPriority(request.priority());
		task.setStatus(TaskStatus.TODO);

		ProjectTask savedTask = taskRepository.save(task);

		if (request.steps() != null) {
			for (int i = 0; i < request.steps().size(); i++) {
				TaskStep step = new TaskStep();
				step.setTask(savedTask);
				step.setStepNumber(i + 1);
				step.setDescription(request.steps().get(i));
				step.setStatus(TaskStatus.TODO);
				stepRepository.save(step);
			}
		}

		return toDTO(savedTask);
	}

	@Transactional
	public TaskDTO createTask(Object body) {
		if (!(body instanceof java.util.Map))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body must be a CreateTaskRequest");
		java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
		Object actualBody = map.containsKey("body") ? map.get("body") : body;
		CreateTaskRequest req = convertBody(actualBody, CreateTaskRequest.class);
		return createTask(req);
	}

	@Transactional
	public TaskDTO updateTask(Long id, TaskDTO taskDTO) {
		ProjectTask task = taskRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + id));

		task.setTitle(taskDTO.title());
		task.setDescription(taskDTO.description());
		task.setStatus(taskDTO.status());
		task.setPriority(taskDTO.priority());
		task.setUpdatedAt(LocalDateTime.now());

		return toDTO(taskRepository.save(task));
	}

	@Transactional
	public TaskDTO updateTask(Long id, Object body) {
		if (id == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'id' is required for op=update");
		Object actualBody = unwrapBody(body);
		TaskDTO dto = convertBody(actualBody, TaskDTO.class);
		return updateTask(id, dto);
	}

	@Transactional
	public TaskDTO updateStepStatus(Long taskId, Long stepId, TaskStatus status) {
		TaskStep step = stepRepository.findById(stepId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found: " + stepId));

		if (!step.getTask().getId().equals(taskId)) {
			throw new IllegalArgumentException("Step does not belong to the specified task");
		}

		step.setStatus(status);
		stepRepository.save(step);

		// Auto-update task status
		ProjectTask task = taskRepository.findByIdForUpdate(step.getTask().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + step.getTask().getId()));
		updateTaskStatusFromSteps(task);

		return toDTO(taskRepository.save(task));
	}

	@Transactional
	public TaskDTO updateStepStatus(Long taskId, Long stepId, String status) {
		if (taskId == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'id' is required for op=update-step");
		if (stepId == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'stepId' is required");
		if (status == null || status.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'status' is required");
		TaskStatus taskStatus;
		try {
			taskStatus = TaskStatus.valueOf(status.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status '" + status + "'. Allowed: " + java.util.Arrays.toString(TaskStatus.values()));
		}
		return updateStepStatus(taskId, stepId, taskStatus);
	}

	private void updateTaskStatusFromSteps(ProjectTask task) {
		List<TaskStep> steps = task.getSteps();
		if (steps.isEmpty())
			return;

		boolean allCompleted = steps.stream().allMatch(s -> s.getStatus() == TaskStatus.COMPLETED);
		boolean anyInProgress = steps.stream()
				.anyMatch(s -> s.getStatus() == TaskStatus.IN_PROGRESS || s.getStatus() == TaskStatus.COMPLETED);

		if (allCompleted) {
			task.setStatus(TaskStatus.COMPLETED);
		} else if (anyInProgress) {
			task.setStatus(TaskStatus.IN_PROGRESS);
		} else {
			task.setStatus(TaskStatus.TODO);
		}
	}

	@Transactional
	public void deleteTask(Long id) {
		taskRepository.deleteById(id);
	}

	private TaskDTO toDTO(ProjectTask task) {
		return new TaskDTO(task.getId(), task.getProject().getId(), task.getTitle(), task.getDescription(),
				task.getStatus(), task.getPriority(), task.getCreatedAt(), task.getUpdatedAt(),
				task.getSteps().stream().map(this::toStepDTO).toList());
	}

	private TaskStepDTO toStepDTO(TaskStep step) {
		return new TaskStepDTO(step.getId(), step.getStepNumber(), step.getDescription(), step.getStatus(),
				step.getCompletedAt());
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

	private Object unwrapBody(Object body) {
		if (body instanceof java.util.Map) {
			java.util.Map<?, ?> map = (java.util.Map<?, ?>) body;
			if (map.containsKey("body")) {
				return map.get("body");
			}
		}
		return body;
	}
}
