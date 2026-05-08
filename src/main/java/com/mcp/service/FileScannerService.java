package com.mcp.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.eclipse.jgit.ignore.IgnoreNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;

@Service
public class FileScannerService {

    private static final Logger logger = LoggerFactory.getLogger(FileScannerService.class);
    private final ProjectRepository projectRepository;
    private final FileIndexerService fileIndexerService;
    private final ExecutorService scanExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static final List<String> INDEXABLE_EXTENSIONS = List.of(
            ".java", ".ts", ".tsx", ".vue", ".js", ".jsx", ".html", ".css", ".json", ".md", ".yaml", ".yml",
            ".properties", ".sql", ".pdf");

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

        // Enable bulk mode to optimize indexing
        fileIndexerService.getLuceneIndexService().setBulkMode(projectId, true);

        try {
            IgnoreNode ignoreNode = new IgnoreNode();
            Path gitIgnorePath = root.resolve(".gitignore");
            if (Files.exists(gitIgnorePath)) {
                try (InputStream is = new FileInputStream(gitIgnorePath.toFile())) {
                    ignoreNode.parse(is);
                } catch (IOException e) {
                    logger.warn("Could not parse .gitignore for project {}: {}", projectId, e.getMessage());
                }
            }

            try (Stream<Path> paths = Files.walk(root)) {
                List<CompletableFuture<Void>> futures = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String relativePath = root.relativize(path).toString().replace("\\", "/");

                            // Always skip .git directory and the .gitignore file itself
                            if (relativePath.startsWith(".git/") || relativePath.equals(".git")
                                    || relativePath.equals(".gitignore")) {
                                return false;
                            }

                            // Check if ignored by .gitignore rules
                            if (ignoreNode.isIgnored(relativePath, false) == IgnoreNode.MatchResult.IGNORED) {
                                return false;
                            }

                            // Default fallback filters for common large/generated directories
                            if (relativePath.contains("node_modules/") ||
                                    relativePath.contains("target/") ||
                                    relativePath.contains("dist/")) {
                                return false;
                            }

                            return isIndexable(path);
                        })
                        .map(path -> CompletableFuture.runAsync(() -> fileIndexerService.indexFile(projectId, path),
                                scanExecutor))
                        .toList();

                logger.info("Submitted {} indexing tasks for project {}", futures.size(), project.getName());
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            } catch (Exception e) {
                logger.error("Error during project scan for project {}", projectId, e);
            }
        } finally {
            // Disable bulk mode and force a final refresh
            fileIndexerService.getLuceneIndexService().setBulkMode(projectId, false);
        }
        logger.info("Scan for project {} completed.", project.getName());
    }

    private boolean isIndexable(Path path) {
        String filename = path.toString().toLowerCase();
        return INDEXABLE_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith(ext));
    }
}
