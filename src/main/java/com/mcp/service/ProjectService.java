package com.mcp.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.entity.Project;
import com.mcp.entity.ProjectTask;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.ProjectRuleRepository;
import com.mcp.repository.ProjectTaskRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.repository.SymbolCallRepository;
import com.mcp.repository.SymbolRepository;

@Service
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectRepository projectRepository;
    private final FileScannerService fileScannerService;
    private final SymbolRepository symbolRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final SkillRepository skillRepository;
    private final SymbolCallRepository symbolCallRepository;
    private final GitInfoService gitInfoService;
    private final LuceneIndexService luceneIndexService;
    private final ProjectTaskRepository projectTaskRepository;
    private final ProjectRuleRepository projectRuleRepository;

    @Value("classpath:skills/global/jcb/SKILL.md")
    private Resource jcbSkillResource;

    public ProjectService(ProjectRepository projectRepository,
            FileScannerService fileScannerService,
            SymbolRepository symbolRepository,
            FileMetadataRepository fileMetadataRepository,
            SkillRepository skillRepository,
            SymbolCallRepository symbolCallRepository,
            GitInfoService gitInfoService,
            LuceneIndexService luceneIndexService,
            ProjectTaskRepository projectTaskRepository,
            ProjectRuleRepository projectRuleRepository) {
        this.projectRepository = projectRepository;
        this.fileScannerService = fileScannerService;
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.skillRepository = skillRepository;
        this.symbolCallRepository = symbolCallRepository;
        this.gitInfoService = gitInfoService;
        this.luceneIndexService = luceneIndexService;
        this.projectTaskRepository = projectTaskRepository;
        this.projectRuleRepository = projectRuleRepository;
    }

    @Transactional
    public Project createProject(String name, String rootPath) throws IOException {
        Project project = new Project(name, rootPath);
        project.setStatus(com.mcp.model.ProjectStatus.INDEXING);
        final Project savedProject = projectRepository.save(project);

        // Create a background task for indexing
        ProjectTask indexTask = new ProjectTask();
        indexTask.setProject(savedProject);
        indexTask.setTitle("Initial Codebase Indexing");
        indexTask.setDescription("Automatically indexing " + name + " at " + rootPath);
        indexTask.setStatus(com.mcp.model.TaskStatus.IN_PROGRESS);
        ProjectTask savedTask = projectTaskRepository.save(indexTask);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logger.info("Transaction committed for project {}. Starting initial scan in background...",
                        savedProject.getId());
                // We use the executor to make it truly background
                CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                    try {
                        fileScannerService.scanProject(savedProject.getId(), savedTask.getId());
                    } catch (Exception e) {
                        logger.error("Failed to start scan for new project {}", savedProject.getId(), e);
                        savedTask.setStatus(com.mcp.model.TaskStatus.FAILED);
                        projectTaskRepository.save(savedTask);
                    }
                });

                future.orTimeout(10, TimeUnit.MINUTES).exceptionally(ex -> {
                    logger.error("Async scan timed out or failed for project {}: {}", savedProject.getId(), ex.getMessage());
                    future.cancel(true); // Attempt to interrupt the task
                    savedTask.setStatus(com.mcp.model.TaskStatus.FAILED);
                    projectTaskRepository.save(savedTask);
                    return null;
                });
            }
        });

        return savedProject;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reindexProject(Long id) {
        Project project = getProject(id);
        logger.info("Triggering manual Git-based re-index for project: {}", project.getName());

        java.util.Set<String> changedFiles = gitInfoService.getChangedFilePaths(id);
        if (changedFiles.isEmpty()) {
            logger.info("No changed files detected by Git for project: {}", project.getName());
            return;
        }

        // Run async without transaction — no DB writes needed
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
            logger.info("Starting partial scan for project {} based on Git changes...", id);
            try {
                fileScannerService.scanChangedFiles(id, changedFiles);
            } catch (Exception e) {
                logger.error("Failed partial scan for project {}", id, e);
            }
        });

        future.orTimeout(10, TimeUnit.MINUTES).exceptionally(ex -> {
            logger.error("Async re-index timed out or failed for project {}: {}", id, ex.getMessage());
            future.cancel(true);
            return null;
        });
    }

    /**
     * Returns file and symbol counts for a single project.
     * Centralises repository access that was previously leaked into ProjectController.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProjectStats(Long id) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("projectId", id);
        stats.put("fileCount", fileMetadataRepository.countByProjectId(id));
        stats.put("symbolCount", symbolRepository.countByProjectId(id));
        return stats;
    }

    public String getGlobalSkillContent() {
        if (jcbSkillResource != null && jcbSkillResource.exists()) {
            try (java.io.InputStream is = jcbSkillResource.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                logger.error("Failed to read global skill", e);
            }
        }
        return "";
    }

    public Map<String, Object> buildProjectOpResponse(Long projectId, String op, Project projectOverride) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", "success");
        if (op != null) {
            response.put("op", op);
        }
        if (projectOverride != null) {
            response.put("project", projectOverride);
        }
        response.put("stats", getProjectStats(projectId));
        response.put("globalSkill", getGlobalSkillContent());
        return response;
    }

    /**
     * Returns a summary list of all projects including file and symbol counts.
     * Centralises repository access that was previously leaked into ProjectController.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllProjectSummaries() {
        return projectRepository.findAllProjectSummaries().stream().map(row -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", row[0]);
            map.put("name", row[1]);
            map.put("rootPath", row[2]);
            map.put("fileCount", row[3]);
            map.put("symbolCount", row[4]);
            return map;
        }).toList();
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProject(id);

        // 2. Clean up associated data in DB (Dependent records first)

        projectTaskRepository.deleteByProjectId(id);
        projectRuleRepository.deleteByProjectId(id);
        symbolCallRepository.deleteByProjectId(id);
        symbolRepository.deleteByProjectId(id);
        fileMetadataRepository.deleteByProjectId(id);
        skillRepository.deleteByProjectId(id);

        // 3. Delete Lucene indices
        luceneIndexService.deleteIndex(id);

        // 4. Finally delete the project record
        projectRepository.delete(project);

        logger.info("Project {} deleted successfully with all associated data.", id);
    }
}
