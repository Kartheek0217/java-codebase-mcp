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

import org.eclipse.jgit.ignore.IgnoreNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;
import com.mcp.repository.ProjectTaskRepository;

@Service
public class FileScannerService {

	private static final Logger logger = LoggerFactory.getLogger(FileScannerService.class);
	private final ProjectRepository projectRepository;
	private final ProjectTaskRepository projectTaskRepository;
	private final FileIndexerService fileIndexerService;
	private final Executor applicationTaskExecutor;

	private static final List<String> INDEXABLE_EXTENSIONS = List.of(".java", ".ts", ".tsx", ".vue", ".js", ".jsx",
			".html", ".css", ".json", ".md", ".yaml", ".yml", ".properties", ".sql");

	public FileScannerService(ProjectRepository projectRepository,
			ProjectTaskRepository projectTaskRepository,
			FileIndexerService fileIndexerService,
			Executor applicationTaskExecutor) {
		this.projectRepository = projectRepository;
		this.projectTaskRepository = projectTaskRepository;
		this.fileIndexerService = fileIndexerService;
		this.applicationTaskExecutor = applicationTaskExecutor;
	}

	// Runs asynchronously on the applicationTaskExecutor so that HTTP request
	// threads are never blocked waiting for potentially long-running scans.
	// The outer task and inner per-file tasks both use applicationTaskExecutor.
	// This is safe because it is backed by newVirtualThreadPerTaskExecutor()
	// (unbounded virtual threads) — the outer task blocking on .join() cannot
	// starve inner tasks since carrier threads are never held by blocked virtuals.
	public void scanChangedFiles(Long projectId, java.util.Set<String> changedPaths) {
		CompletableFuture.runAsync(() -> {
			Project project = projectRepository.findById(projectId).orElseThrow();
			Path root = Paths.get(project.getRootPath()).toAbsolutePath();
			logger.info("Starting partial scan for {} changed files in project {}", changedPaths.size(),
					project.getName());

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
		}, applicationTaskExecutor);
	}

	public CompletableFuture<Void> scanProject(Long projectId) {
		return scanProject(projectId, null);
	}

	public CompletableFuture<Void> scanProject(Long projectId, Long taskId) {
		return CompletableFuture.runAsync(() -> {
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

				List<Path> filesToIndex = new java.util.ArrayList<>();
				try {
					Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
						@Override
						public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
								java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
							String relativePath = root.relativize(dir).toString().replace("\\", "/");
							if (relativePath.isEmpty()) {
								return java.nio.file.FileVisitResult.CONTINUE;
							}
							// Always skip .git directory
							if (relativePath.equals(".git") || relativePath.startsWith(".git/")) {
								return java.nio.file.FileVisitResult.SKIP_SUBTREE;
							}
							// Check if ignored by .gitignore rules
							if (ignoreNode.isIgnored(relativePath, true) == IgnoreNode.MatchResult.IGNORED) {
								return java.nio.file.FileVisitResult.SKIP_SUBTREE;
							}
							// Default fallback filters for common large/generated directories.
							if (hasPathSegment(relativePath, "node_modules")
									|| hasPathSegment(relativePath, "target")
									|| hasPathSegment(relativePath, "dist")
									|| hasPathSegment(relativePath, "build")
									|| hasPathSegment(relativePath, "bin")
									|| hasPathSegment(relativePath, "logs")
									|| hasPathSegment(relativePath, ".idea")
									|| hasPathSegment(relativePath, ".vscode")
									|| hasPathSegment(relativePath, ".gradle")
									|| hasPathSegment(relativePath, ".settings")) {
								return java.nio.file.FileVisitResult.SKIP_SUBTREE;
							}
							return java.nio.file.FileVisitResult.CONTINUE;
						}

						@Override
						public java.nio.file.FileVisitResult visitFile(Path file,
								java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
							String relativePath = root.relativize(file).toString().replace("\\", "/");
							// Always skip .gitignore file itself
							if (relativePath.equals(".gitignore")) {
								return java.nio.file.FileVisitResult.CONTINUE;
							}
							// Check if ignored by .gitignore rules
							if (ignoreNode.isIgnored(relativePath, false) == IgnoreNode.MatchResult.IGNORED) {
								return java.nio.file.FileVisitResult.CONTINUE;
							}
							if (isIndexable(file)) {
								filesToIndex.add(file);
							}
							return java.nio.file.FileVisitResult.CONTINUE;
						}
					});

					List<CompletableFuture<Void>> futures = filesToIndex.stream()
							.map(path -> CompletableFuture.runAsync(() -> fileIndexerService.indexFile(projectId, path),
									applicationTaskExecutor))
							.toList();

					logger.info("Submitted {} indexing tasks for project {}", futures.size(), project.getName());
					CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

				} catch (Exception e) {
					logger.error("Error during project scan for project {}", projectId, e);
					project.setStatus(com.mcp.model.ProjectStatus.FAILED);
					if (task != null) {
						task.setStatus(com.mcp.model.TaskStatus.BLOCKED);
						projectTaskRepository.save(task);
					}
					throw new RuntimeException(e);
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
		}, applicationTaskExecutor);
	}

	private boolean isIndexable(Path path) {
		String filename = path.toString().toLowerCase();
		return INDEXABLE_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith(ext));
	}

	/**
	 * Returns true if {@code relativePath} contains {@code segment} as an exact
	 * path component (i.e., bounded by '/' on both sides, or at the start/end).
	 * Prevents substring false-positives such as "my-target/" matching "target".
	 */
	private static boolean hasPathSegment(String relativePath, String segment) {
		// Normalise to forward slashes (already done by caller, but be defensive)
		String path = relativePath.replace('\\', '/');
		return path.equals(segment)
				|| path.startsWith(segment + "/")
				|| path.contains("/" + segment + "/")
				|| path.endsWith("/" + segment);
	}
}
