package com.mcp.service;

import com.mcp.entity.Project;
import com.mcp.model.FileEvent;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DirectoryWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcherService.class);

    private final Map<Long, DirectoryWatcher> watchers = new ConcurrentHashMap<>();
    private final ExecutorService watcherExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final FileIndexerService fileIndexerService;

    public DirectoryWatcherService(FileIndexerService fileIndexerService) {
        this.fileIndexerService = fileIndexerService;
    }

    public void startWatching(Project project) throws IOException {
        if (watchers.containsKey(project.getId())) {
            return;
        }

        Path scanDirectory = Paths.get(project.getRootPath()).toAbsolutePath();
        logger.info("Starting DirectoryWatcher for project {} on: {}", project.getName(), scanDirectory);
        
        DirectoryWatcher watcher = DirectoryWatcher.builder()
                .path(scanDirectory)
                .listener(event -> {
                    FileEvent.Type type = switch (event.eventType()) {
                        case CREATE -> FileEvent.Type.CREATED;
                        case MODIFY -> FileEvent.Type.MODIFIED;
                        case DELETE -> FileEvent.Type.DELETED;
                        default -> null;
                    };
                    
                    if (type != null && event.path() != null && event.path().toString().endsWith(".java")) {
                        handleEvent(project.getId(), type, event.path());
                    }
                })
                .build();

        watchers.put(project.getId(), watcher);
        watcherExecutor.submit(() -> {
            try {
                watcher.watch();
            } catch (Exception e) {
                logger.error("Error in DirectoryWatcher for project {}", project.getId(), e);
            }
        });
    }

    private void handleEvent(Long projectId, FileEvent.Type type, Path path) {
        logger.debug("Detected file event in project {}: {} - {}", projectId, type, path);
        if (type == FileEvent.Type.DELETED) {
            fileIndexerService.deleteFileData(projectId, path);
        } else {
            fileIndexerService.indexFile(projectId, path);
        }
    }

    public void stopWatching(Long projectId) {
        DirectoryWatcher watcher = watchers.remove(projectId);
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                logger.error("Error closing watcher for project {}", projectId, e);
            }
        }
    }

    @PreDestroy
    public void stopAll() {
        watchers.keySet().forEach(this::stopWatching);
        watcherExecutor.shutdown();
    }
}
