package com.mcp.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolCall;
import com.mcp.entity.SymbolType;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SymbolCallRepository;
import com.mcp.repository.SymbolRepository;

@Service
public class FileDataPersistenceService {

	private static final Logger logger = LoggerFactory.getLogger(FileDataPersistenceService.class);

	private final SymbolRepository symbolRepository;
	private final SymbolCallRepository symbolCallRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final LuceneIndexService luceneIndexService;
	private final Cache<String, List<Symbol>> symbolCache;

	public FileDataPersistenceService(SymbolRepository symbolRepository,
			SymbolCallRepository symbolCallRepository,
			FileMetadataRepository fileMetadataRepository,
			LuceneIndexService luceneIndexService,
			Cache<String, List<Symbol>> symbolCache) {
		this.symbolRepository = symbolRepository;
		this.symbolCallRepository = symbolCallRepository;
		this.fileMetadataRepository = fileMetadataRepository;
		this.luceneIndexService = luceneIndexService;
		this.symbolCache = symbolCache;
	}

	@Transactional
	public void saveFileData(Long projectId, String filePath, String checksum, long fileSize, LocalDateTime now,
			List<Symbol> symbols, List<FileIndexerService.CallInfo> calls, String dependencies, FileMetadata existingMetadata) {
		// Clear existing symbols and their calls from this file

		symbolRepository.deleteByProjectIdAndFilePath(projectId, filePath);
		symbolCallRepository.deleteByProjectIdAndCallerFilePath(projectId, filePath);
		symbolCache.invalidate(projectId + ":" + filePath);

		if (symbols != null && !symbols.isEmpty()) {
			for (Symbol s : symbols) {
				s.setProjectId(projectId);
				s.setFilePath(filePath);
				s.setLastModified(now);
			}
			List<Symbol> savedSymbols = symbolRepository.saveAll(symbols);
			symbolCache.put(projectId + ":" + filePath, new ArrayList<>(savedSymbols));

			if (calls != null && !calls.isEmpty()) {
				List<SymbolCall> symbolCalls = calls.stream()
						.flatMap(ci -> savedSymbols.stream()
								.filter(s -> s.getName().equals(ci.callerName()) && s.getType() == SymbolType.METHOD)
								.findFirst()
								.stream()
								.map(caller -> {
									SymbolCall sc = new SymbolCall();
									sc.setProjectId(projectId);
									sc.setCallerId(caller.getId());
									sc.setCallerFilePath(filePath);
									sc.setCalleeName(ci.calleeName());
									return sc;
								}))
						.toList();
				symbolCallRepository.saveAll(symbolCalls);
			}
		}

		FileMetadata metadata = existingMetadata;
		if (metadata == null) {
			metadata = new FileMetadata();
		}

		if (metadata.getProjectId() == null) {
			metadata.setProjectId(projectId);
			metadata.setFilePath(filePath);
		}
		metadata.setChecksum(checksum);
		metadata.setFileSize(fileSize);
		metadata.setLastScanned(now);
		metadata.setDependencies(dependencies);
		fileMetadataRepository.save(metadata);
	}

	@Transactional
	public void deleteFileData(Long projectId, Path path) {
		String filePath = path.toAbsolutePath().toString();
		logger.info("Deleting data for file: {} in project {}", filePath, projectId);

		symbolRepository.deleteByProjectIdAndFilePath(projectId, filePath);
		fileMetadataRepository.deleteById(new FileMetadataId(projectId, filePath));
		luceneIndexService.deleteFileContent(projectId, filePath);
		symbolCache.invalidate(projectId + ":" + filePath);
	}
}
