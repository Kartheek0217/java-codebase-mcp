package com.mcp.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
	private final com.mcp.repository.ProjectTaskRepository projectTaskRepository;
	private final FileIndexerService fileIndexerService;
	private final Executor applicationTaskExecutor;

	private static final List<String> INDEXABLE_EXTENSIONS = List.of(".java", ".ts", ".tsx", ".vue", ".js", ".jsx",
			".html", ".css", ".json", ".md", ".yaml", ".yml", ".properties", ".sql");

	public FileScannerService(ProjectRepository projectRepository,
			com.mcp.repository.ProjectTaskRepository projectTaskRepository,
			FileIndexerService fileIndexerService,
			Executor applicationTaskExecutor) {
		this.projectRepository = projectRepository;
		this.projectTaskRepository = projectTaskRepository;
		this.fileIndexerService = fileIndexerService;
		this.applicationTaskExecutor = applicationTaskExecutor;
	}

	public void scanChangedFiles(Long projectId, java.util.Set<String> changedPaths) {
		Project project = projectRepository.findById(projectId).orElseThrow();
		Path root = Paths.get(project.getRootPath()).toAbsolutePath();
		logger.info("Starting partial scan for {} changed files in project {}", changedPaths.size(), project.getName());

		project.setStatus(com.mcp.model.ProjectStatus.INDEXING);
		projectRepository.save(project);

		try {
			List<CompletableFuture<Void>> futures = changedPaths.stream().map(relPath -> {
				Path fullPath = root.resolve(relPath).toAbsolutePath();
				return CompletableFuture.runAsync(() -> {
					if (Files.exists(fullPath)) {
						if (isIndexable(fullPath)) {
							fileIndexerService.indexFile(projectId, fullPath);
						}
					} else {
						// File was deleted
						fileIndexerService.deleteFileData(projectId, fullPath);
					}
				}, applicationTaskExecutor);
			}).toList();

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			project.setStatus(com.mcp.model.ProjectStatus.COMPLETED);
			logger.info("Partial scan for project {} completed.", project.getName());
		} catch (Exception e) {
			logger.error("Partial scan failed for project {}", projectId, e);
			project.setStatus(com.mcp.model.ProjectStatus.FAILED);
		} finally {
			projectRepository.save(project);
		}
	}

	public void scanProject(Long projectId) throws IOException {
		scanProject(projectId, null);
	}

	public void scanProject(Long projectId, Long taskId) throws IOException {
		Project project = projectRepository.findById(projectId).orElseThrow();
		Path root = Paths.get(project.getRootPath()).toAbsolutePath();
		logger.info("Starting scan for project {}: {}", project.getName(), root);

		project.setStatus(com.mcp.model.ProjectStatus.INDEXING);
		projectRepository.save(project);

		com.mcp.entity.ProjectTask task = null;
		if (taskId != null) {
			task = projectTaskRepository.findById(taskId).orElse(null);
			if (task != null) {
				task.setStatus(com.mcp.model.TaskStatus.IN_PROGRESS);
				projectTaskRepository.save(task);
			}
		}

		if (!Files.exists(root)) {
			logger.warn("Project root does not exist: {}", root);
			project.setStatus(com.mcp.model.ProjectStatus.FAILED);
			projectRepository.save(project);
			if (task != null) {
				task.setStatus(com.mcp.model.TaskStatus.BLOCKED);
				projectTaskRepository.save(task);
			}
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
				List<CompletableFuture<Void>> futures = paths.filter(Files::isRegularFile).filter(path -> {
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
					if (relativePath.contains("node_modules/") || relativePath.contains("target/")
							|| relativePath.contains("dist/") || relativePath.contains("build/")
							|| relativePath.contains("bin/") || relativePath.contains("logs/")
							|| relativePath.contains(".idea/") || relativePath.contains(".vscode/")
							|| relativePath.contains(".gradle/") || relativePath.contains(".settings/")) {
						return false;
					}

					return isIndexable(path);
				}).map(path -> CompletableFuture.runAsync(() -> fileIndexerService.indexFile(projectId, path),
						applicationTaskExecutor)).toList();

				logger.info("Submitted {} indexing tasks for project {}", futures.size(), project.getName());
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			} catch (Exception e) {
				logger.error("Error during project scan for project {}", projectId, e);
				project.setStatus(com.mcp.model.ProjectStatus.FAILED);
				if (task != null) {
					task.setStatus(com.mcp.model.TaskStatus.BLOCKED);
					projectTaskRepository.save(task);
				}
				throw e;
			}
			project.setStatus(com.mcp.model.ProjectStatus.COMPLETED);
			if (task != null) {
				task.setStatus(com.mcp.model.TaskStatus.COMPLETED);
				projectTaskRepository.save(task);
			}
		} finally {
			// Disable bulk mode and force a final refresh
			fileIndexerService.getLuceneIndexService().setBulkMode(projectId, false);
			projectRepository.save(project);
		}
		logger.info("Scan for project {} completed.", project.getName());
	}

	private boolean isIndexable(Path path) {
		String filename = path.toString().toLowerCase();
		return INDEXABLE_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith(ext));
	}
}
