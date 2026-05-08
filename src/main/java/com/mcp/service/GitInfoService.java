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

    private String commitHash;
    private String commitMessage;
    private String branchName;
    private boolean gitAvailable = false;

    public GitInfoService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostConstruct
    public void init() {
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .readEnvironment()
                    .findGitDir(new File("."))
                    .build();

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
        return repositoryCache.computeIfAbsent(projectId, id -> {
            try {
                Project project = projectRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Project not found: " + id));

                FileRepositoryBuilder builder = new FileRepositoryBuilder();
                Repository repository = builder
                        .readEnvironment()
                        .findGitDir(new File(project.getRootPath()))
                        .build();

                if (repository == null || !repository.getDirectory().exists()) {
                    throw new RuntimeException("Git repository not found for project: " + project.getName());
                }
                return repository;
            } catch (IOException e) {
                throw new RuntimeException("Error opening Git repository", e);
            }
        });
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
