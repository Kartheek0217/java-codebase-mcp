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
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class DirectoryWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcherService.class);
    private static final long DEBOUNCE_DELAY_MS = 500;

    private final Map<Long, DirectoryWatcher> watchers = new ConcurrentHashMap<>();
    private final Map<Path, ScheduledFuture<?>> debounceMap = new ConcurrentHashMap<>();
    private final ExecutorService watcherExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    private final FileIndexerService fileIndexerService;

    private static final List<String> INDEXABLE_EXTENSIONS = List.of(
            ".java", ".ts", ".tsx", ".vue", ".js", ".jsx", ".html", ".css", ".json", ".md", ".yaml", ".yml", ".properties", ".sql"
    );

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
                    
                    if (type != null && event.path() != null && isIndexable(event.path())) {
                        debounce(project.getId(), type, event.path());
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

    private void debounce(Long projectId, FileEvent.Type type, Path path) {
        ScheduledFuture<?> existing = debounceMap.get(path);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                handleEvent(projectId, type, path);
            } finally {
                debounceMap.remove(path);
            }
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

        debounceMap.put(path, future);
    }

    private boolean isIndexable(Path path) {
        String filename = path.toString().toLowerCase();
        return INDEXABLE_EXTENSIONS.stream().anyMatch(ext -> filename.endsWith(ext));
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
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
