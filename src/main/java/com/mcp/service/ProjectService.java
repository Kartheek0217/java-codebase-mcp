package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.SymbolRepository;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SkillRepository;
import com.mcp.repository.CrawlJobRepository;
import com.mcp.repository.CrawledPageRepository;
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
    private final SymbolRepository symbolRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final SkillRepository skillRepository;
    private final CrawlJobRepository crawlJobRepository;
    private final CrawledPageRepository crawledPageRepository;
    private final LuceneIndexService luceneIndexService;

    public ProjectService(ProjectRepository projectRepository,
            FileScannerService fileScannerService,
            DirectoryWatcherService watcherService,
            SymbolRepository symbolRepository,
            FileMetadataRepository fileMetadataRepository,
            SkillRepository skillRepository,
            CrawlJobRepository crawlJobRepository,
            CrawledPageRepository crawledPageRepository,
            LuceneIndexService luceneIndexService) {
        this.projectRepository = projectRepository;
        this.fileScannerService = fileScannerService;
        this.watcherService = watcherService;
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.skillRepository = skillRepository;
        this.crawlJobRepository = crawlJobRepository;
        this.crawledPageRepository = crawledPageRepository;
        this.luceneIndexService = luceneIndexService;
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
    public void reindexProject(Long id) {
        Project project = getProject(id);
        logger.info("Triggering full re-index for project: {}", project.getName());

        // 1. Stop watchers
        watcherService.stopWatching(id);

        // 2. Clean up associated data in DB
        symbolRepository.deleteByProjectId(id);
        fileMetadataRepository.deleteByProjectId(id);
        // We keep skills and crawl data unless specifically asked, 
        // but for code analysis we need symbols and file metadata gone.

        // 3. Delete Lucene indices
        luceneIndexService.deleteIndex(id);

        // 4. Trigger new scan
        try {
            watcherService.startWatching(project);
            fileScannerService.scanProject(id);
        } catch (IOException e) {
            logger.error("Failed to restart watcher/scan during re-indexing for project {}", id, e);
            throw new RuntimeException("Re-indexing failed", e);
        }
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProject(id);
        
        // 1. Stop watchers
        watcherService.stopWatching(id);
        
        // 2. Clean up associated data in DB
        symbolRepository.deleteByProjectId(id);
        fileMetadataRepository.deleteByProjectId(id);
        skillRepository.deleteByProjectId(id);
        crawledPageRepository.deleteByProjectId(id);
        crawlJobRepository.deleteByProjectId(id);
        
        // 3. Delete Lucene indices
        luceneIndexService.deleteIndex(id);
        
        // 4. Finally delete the project record
        projectRepository.delete(project);
        
        logger.info("Project {} deleted successfully with all associated data.", id);
    }
}
