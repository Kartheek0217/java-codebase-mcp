package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Service
public class FileScannerService {

    private static final Logger logger = LoggerFactory.getLogger(FileScannerService.class);
    private final ProjectRepository projectRepository;
    private final FileIndexerService fileIndexerService;
    private final ExecutorService scanExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static final List<String> INDEXABLE_EXTENSIONS = List.of(
            ".java", ".ts", ".tsx", ".vue", ".js", ".jsx", ".html", ".css", ".json", ".md", ".yaml", ".yml", ".properties", ".sql"
    );

    public FileScannerService(ProjectRepository projectRepository, FileIndexerService fileIndexerService) {
        this.projectRepository = projectRepository;
        this.fileIndexerService = fileIndexerService;
    }

    public void scanProject(Long projectId) throws IOException {
        Project project = projectRepository.findById(projectId).orElseThrow();
        Path root = Paths.get(project.getRootPath()).toAbsolutePath();
        logger.info("Starting scan for project {}: {}", project.getName(), root);

        if (!Files.exists(root)) {
            logger.warn("Project root does not exist: {}", root);
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> filesToIndex = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("node_modules"))
                    .filter(path -> !path.toString().contains(".git"))
                    .filter(path -> !path.toString().contains("target/"))
                    .filter(path -> !path.toString().contains("dist/"))
                    .filter(path -> isIndexable(path))
                    .toList();
            
            logger.info("Found {} indexable files in project {}", filesToIndex.size(), project.getName());

            List<CompletableFuture<Void>> futures = filesToIndex.stream()
                    .map(path -> CompletableFuture.runAsync(() -> fileIndexerService.indexFile(projectId, path), scanExecutor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .handle((v, ex) -> {
                        for (int i = 0; i < futures.size(); i++) {
                            try {
                                futures.get(i).get();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                logger.error("Scan interrupted", e);
                                break;
                            } catch (ExecutionException e) {
                                logger.error("Scan failed for file: {}", filesToIndex.get(i), e.getCause());
                            }
                        }
                        return null;
                    }).join();
        } catch (Exception e) {
            logger.error("Error walking directory tree for project {}", projectId, e);
        }
        logger.info("Scan for project {} completed.", project.getName());
    }

    private boolean isIndexable(Path path) {
        String filename = path.toString().toLowerCase();
        return INDEXABLE_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith(ext));
    }
}
