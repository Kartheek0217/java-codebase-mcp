package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.repository.ProjectRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitInfoService {

    private static final Logger logger = LoggerFactory.getLogger(GitInfoService.class);

    private final ProjectRepository projectRepository;
    private final Map<Long, Repository> repositoryCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastAccessTimes = new ConcurrentHashMap<>();

    // volatile: written once in @PostConstruct, read from any thread thereafter
    private volatile String commitHash;
    private volatile String commitMessage;
    private volatile String branchName;
    private volatile boolean gitAvailable = false;

    public GitInfoService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostConstruct
    public void init() {
        // Use try-with-resources so the global Repository is closed after we've
        // read the startup info — it is not cached, unlike per-project repositories.
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            try (Repository repository = builder
                    .readEnvironment()
                    .findGitDir(new File("."))
                    .build()) {

                if (repository == null || !repository.getDirectory().exists()) {
                    logger.warn("Global Git directory not found");
                    return;
                }

                ObjectId head = repository.resolve("HEAD");
                if (head != null) {
                    commitHash = head.getName().substring(0, Math.min(8, head.getName().length()));

                    try (Git git = new Git(repository)) {
                        commitMessage = git.log().setMaxCount(1).call().iterator().next().getFullMessage().trim();
                    }

                    branchName = repository.getBranch();
                    gitAvailable = true;

                    logger.info("Global Git info loaded: commit={}, branch={}", commitHash, branchName);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not retrieve global Git information: {}", e.getMessage());
            gitAvailable = false;
        }
    }

    public Optional<Repository> getRepositoryOptional(Long projectId) {
        try {
            return Optional.of(getRepository(projectId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Repository getRepository(Long projectId) throws IOException {
        // Avoid computeIfAbsent with a blocking IO lambda — ConcurrentHashMap docs warn
        // this can deadlock when the lambda itself blocks on map operations.
        Repository existing = repositoryCache.get(projectId);
        if (existing != null) {
            lastAccessTimes.put(projectId, System.currentTimeMillis());
            return existing;
        }
        synchronized (this) {
            // Double-check inside lock
            existing = repositoryCache.get(projectId);
            if (existing != null) {
                lastAccessTimes.put(projectId, System.currentTimeMillis());
                return existing;
            }
            try {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

                FileRepositoryBuilder builder = new FileRepositoryBuilder();
                Repository repository = builder
                        .readEnvironment()
                        .findGitDir(new File(project.getRootPath()))
                        .build();

                if (repository == null || !repository.getDirectory().exists()) {
                    throw new RuntimeException("Git repository not found for project: " + project.getName());
                }
                repositoryCache.put(projectId, repository);
                lastAccessTimes.put(projectId, System.currentTimeMillis());
                return repository;
            } catch (IOException e) {
                throw new IOException("Error opening Git repository for project " + projectId, e);
            }
        }
    }

    @Scheduled(fixedDelay = 1_800_000) // Run every 30 minutes
    public void cleanupIdleRepositories() {
        long now = System.currentTimeMillis();
        long idleThreshold = 30 * 60 * 1000; // 30 minutes

        var iterator = lastAccessTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Long projectId = entry.getKey();
            Long lastAccess = entry.getValue();
            if (now - lastAccess > idleThreshold) {
                logger.info("Closing idle Git repository for project {}", projectId);
                // Note: repositoryCache.remove + repo.close() are not atomic.
                // A concurrent getRepository call could re-add an entry between
                // the remove and close; that new entry would be a fresh handle and
                // is not affected. The closed handle here is simply discarded.
                Repository repo = repositoryCache.remove(projectId);
                if (repo != null) {
                    repo.close();
                }
                iterator.remove();
            }
        }
    }

    public Map<String, Object> getProjectStatus(Long projectId) {
        try {
            Repository repository = getRepository(projectId);
            try (Git git = new Git(repository)) {
                Status status = git.status().call();
                Map<String, Object> result = new HashMap<>();
                result.put("branch", repository.getBranch());
                result.put("clean", status.isClean());
                result.put("modified", status.getModified());
                result.put("added", status.getAdded());
                result.put("removed", status.getRemoved());
                result.put("missing", status.getMissing());
                result.put("untracked", status.getUntracked());
                result.put("staged", status.getChanged());
                return result;
            }
        } catch (Exception e) {
            logger.error("Error getting Git status for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Could not get Git status: " + e.getMessage());
        }
    }

    public Set<String> getChangedFilePaths(Long projectId) {
        try {
            Repository repository = getRepository(projectId);
            try (Git git = new Git(repository)) {
                Status status = git.status().call();
                Set<String> changedFiles = new java.util.HashSet<>();
                changedFiles.addAll(status.getModified());
                changedFiles.addAll(status.getAdded());
                changedFiles.addAll(status.getRemoved());
                changedFiles.addAll(status.getMissing());
                changedFiles.addAll(status.getUntracked());
                changedFiles.addAll(status.getChanged()); // staged
                return changedFiles;
            }
        } catch (Exception e) {
            logger.error("Error getting changed files for project {}: {}", projectId, e.getMessage());
            return java.util.Set.of();
        }
    }

    public void stageFiles(Long projectId, List<String> patterns) {
        try {
            Repository repository = getRepository(projectId);
            try (Git git = new Git(repository)) {
                var addCommand = git.add();
                for (String pattern : patterns) {
                    addCommand.addFilepattern(pattern);
                }
                addCommand.call();

                var rmCommand = git.rm();
                boolean hasDeletions = false;
                Status status = git.status().call();
                Set<String> missing = status.getMissing();
                for (String pattern : patterns) {
                    if (missing.contains(pattern)) {
                        rmCommand.addFilepattern(pattern);
                        hasDeletions = true;
                    }
                }
                if (hasDeletions) {
                    rmCommand.call();
                }
            }
        } catch (Exception e) {
            logger.error("Error staging files for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Could not stage files: " + e.getMessage());
        }
    }

    public void discardChanges(Long projectId, List<String> patterns) {
        try {
            Repository repository = getRepository(projectId);
            try (Git git = new Git(repository)) {
                var checkoutCommand = git.checkout();
                for (String pattern : patterns) {
                    checkoutCommand.addPath(pattern);
                }
                checkoutCommand.call();
            }
        } catch (Exception e) {
            logger.error("Error discarding changes for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Could not discard changes: " + e.getMessage());
        }
    }

    public String commit(Long projectId, String message) {
        try {
            Repository repository = getRepository(projectId);
            try (Git git = new Git(repository)) {
                var commit = git.commit()
                        .setMessage(message)
                        .call();
                return commit.getName().substring(0, 8);
            }
        } catch (Exception e) {
            logger.error("Error committing for project {}: {}", projectId, e.getMessage());
            throw new RuntimeException("Could not commit changes: " + e.getMessage());
        }
    }

    @PreDestroy
    public void closeAllRepositories() {
        repositoryCache.values().forEach(Repository::close);
        repositoryCache.clear();
    }

    public String getCommitHash() {
        return commitHash != null ? commitHash : "unknown";
    }

    public String getCommitMessage() {
        return commitMessage != null ? commitMessage : "N/A";
    }

    public String getBranchName() {
        return branchName != null ? branchName : "N/A";
    }

    public boolean isGitAvailable() {
        return gitAvailable;
    }
}
