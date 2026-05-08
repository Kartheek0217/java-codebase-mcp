package com.mcp.service;

import com.mcp.entity.FileMetadata;
import com.mcp.repository.FileMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationService.class);
    private final FileMetadataRepository fileMetadataRepository;
    private final FileScannerService fileScannerService;
    private final FileIndexerService fileIndexerService;

    public ReconciliationService(FileMetadataRepository fileMetadataRepository,
            FileScannerService fileScannerService,
            FileIndexerService fileIndexerService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileScannerService = fileScannerService;
        this.fileIndexerService = fileIndexerService;
    }

    public void reconcileProject(Long projectId) {
        logger.info("Starting reconciliation for project {}...", projectId);

        // 1. Identify orphaned records (in DB but file deleted)
        List<FileMetadata> allMetadata = fileMetadataRepository.findByProjectId(projectId);
        for (FileMetadata metadata : allMetadata) {
            Path path = Paths.get(metadata.getFilePath());
            if (!Files.exists(path)) {
                logger.info("Found orphaned record in project {}, deleting: {}", projectId, metadata.getFilePath());
                fileIndexerService.deleteFileData(projectId, path);
            }
        }

        // 2. Trigger full scan to catch missing/changed files
        try {
            fileScannerService.scanProject(projectId);
        } catch (IOException e) {
            logger.error("Error during reconciliation scan for project {}", projectId, e);
        }

        logger.info("Reconciliation for project {} completed.", projectId);
    }
}
