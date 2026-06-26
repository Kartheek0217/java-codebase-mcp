package com.mcp.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mcp.entity.FileMetadata;
import com.mcp.repository.FileMetadataRepository;

@Service
public class ReconciliationService {

	private static final Logger logger = LoggerFactory.getLogger(ReconciliationService.class);
	private final FileMetadataRepository fileMetadataRepository;
	private final FileScannerService fileScannerService;
	private final FileIndexerService fileIndexerService;

	public ReconciliationService(FileMetadataRepository fileMetadataRepository, FileScannerService fileScannerService,
			FileIndexerService fileIndexerService) {
		this.fileMetadataRepository = fileMetadataRepository;
		this.fileScannerService = fileScannerService;
		this.fileIndexerService = fileIndexerService;
	}

	@org.springframework.transaction.annotation.Transactional
	public void reconcileProject(Long projectId) {
		logger.info("Starting reconciliation for project {}...", projectId);

		// 1. Identify orphaned records (in DB but file deleted)
		List<FileMetadata> allMetadata = fileMetadataRepository.findByProjectId(projectId);
		List<String> orphanedPaths = new java.util.ArrayList<>();

		for (FileMetadata metadata : allMetadata) {
			Path path = Paths.get(metadata.getFilePath());
			if (!Files.exists(path)) {
				logger.info("Found orphaned record in project {}, marking for deletion: {}", projectId,
						metadata.getFilePath());
				orphanedPaths.add(metadata.getFilePath());
			}
		}

		if (!orphanedPaths.isEmpty()) {
			logger.info("Batch deleting {} orphaned records for project {}", orphanedPaths.size(), projectId);
			// Batch delete symbols
			fileIndexerService.getSymbolRepository().deleteByProjectIdAndFilePathIn(projectId, orphanedPaths);
			// Batch delete metadata
			fileMetadataRepository.deleteByProjectIdAndFilePathIn(projectId, orphanedPaths);

			org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
				@Override
				public void afterCommit() {
					// Delete from Lucene
					fileIndexerService.getLuceneIndexService().deleteFilesContent(projectId, orphanedPaths);
					
					// 2. Trigger full scan to catch missing/changed files
					try {
						fileScannerService.scanProject(projectId);
					} catch (Exception e) {
						logger.error("Error during reconciliation scan for project {}", projectId, e);
					}
				}
			});
		} else {
			org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						fileScannerService.scanProject(projectId);
					} catch (Exception e) {
						logger.error("Error during reconciliation scan for project {}", projectId, e);
					}
				}
			});
		}

		logger.info("Reconciliation for project {} transaction prepared.", projectId);
	}
}
