package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;

@Service
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectRepository projectRepository;
    private final FileScannerService fileScannerService;
    private final DirectoryWatcherService watcherService;

    public ProjectService(ProjectRepository projectRepository,
            FileScannerService fileScannerService,
            DirectoryWatcherService watcherService) {
        this.projectRepository = projectRepository;
        this.fileScannerService = fileScannerService;
        this.watcherService = watcherService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWatchersOnStartup() {
        logger.info("Restarting watchers for all existing projects...");
        List<Project> projects = projectRepository.findAll();
        for (Project project : projects) {
            try {
                watcherService.startWatching(project);
            } catch (IOException e) {
                logger.error("Failed to start watcher for project {}", project.getId(), e);
            }
        }
    }

    @Transactional
    public Project createProject(String name, String rootPath) throws IOException {
        Project project = new Project(name, rootPath);
        final Project savedProject = projectRepository.save(project);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logger.info("Transaction committed for project {}. Starting scan...", savedProject.getId());
                try {
                    // Start watching
                    watcherService.startWatching(savedProject);
                    // Initial scan
                    fileScannerService.scanProject(savedProject.getId());
                } catch (IOException e) {
                    logger.error("Failed to start scan for new project {}", savedProject.getId(), e);
                }
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
    public void deleteProject(Long id) {
        Project project = getProject(id);
        watcherService.stopWatching(id);
        projectRepository.delete(project);
    }
}
