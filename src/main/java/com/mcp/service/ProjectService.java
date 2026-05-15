package com.mcp.service;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mcp.entity.Project;
import com.mcp.entity.ProjectTask;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.ProjectRepository;
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
    private final com.mcp.repository.ProjectTaskRepository projectTaskRepository;

    public ProjectService(ProjectRepository projectRepository,
            FileScannerService fileScannerService,
            SymbolRepository symbolRepository,
            FileMetadataRepository fileMetadataRepository,
            SkillRepository skillRepository,
            SymbolCallRepository symbolCallRepository,
            GitInfoService gitInfoService,
            LuceneIndexService luceneIndexService,
            com.mcp.repository.ProjectTaskRepository projectTaskRepository) {
        this.projectRepository = projectRepository;
        this.fileScannerService = fileScannerService;
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.skillRepository = skillRepository;
        this.symbolCallRepository = symbolCallRepository;
        this.gitInfoService = gitInfoService;
        this.luceneIndexService = luceneIndexService;
        this.projectTaskRepository = projectTaskRepository;
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
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        fileScannerService.scanProject(savedProject.getId(), savedTask.getId());
                    } catch (IOException e) {
                        logger.error("Failed to start scan for new project {}", savedProject.getId(), e);
                    }
                });
            }
        });

        return savedProject;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
    }

    @Transactional
    public void reindexProject(Long id) {
        Project project = getProject(id);
        logger.info("Triggering manual Git-based re-index for project: {}", project.getName());

        java.util.Set<String> changedFiles = gitInfoService.getChangedFilePaths(id);
        if (changedFiles.isEmpty()) {
            logger.info("No changed files detected by Git for project: {}", project.getName());
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logger.info("Starting partial scan for project {} based on Git changes...", id);
                fileScannerService.scanChangedFiles(id, changedFiles);
            }
        });
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProject(id);

        // 2. Clean up associated data in DB (Dependent records first)

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
