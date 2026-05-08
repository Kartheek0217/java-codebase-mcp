package com.mcp.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Symbol;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileIndexerService {

    private static final Logger logger = LoggerFactory.getLogger(FileIndexerService.class);
    private final SymbolRepository symbolRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final LuceneIndexService luceneIndexService;
    private final Cache<String, List<Symbol>> symbolCache;

    public FileIndexerService(SymbolRepository symbolRepository,
            FileMetadataRepository fileMetadataRepository,
            LuceneIndexService luceneIndexService,
            Cache<String, List<Symbol>> symbolCache) {
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.luceneIndexService = luceneIndexService;
        this.symbolCache = symbolCache;

        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(config);
    }

    public void indexFile(Long projectId, Path path) {
        logger.debug("indexFile called for project {} and path {}", projectId, path);
        try {
            String filePath = path.toAbsolutePath().toString();
            String checksum = computeChecksum(path);
            long fileSize = Files.size(path);
            LocalDateTime now = LocalDateTime.now();

            FileMetadataId id = new FileMetadataId(projectId, filePath);
            FileMetadata metadata = fileMetadataRepository.findById(id).orElse(null);

            if (metadata != null && metadata.getChecksum().equals(checksum)) {
                logger.debug("File unchanged: {}", filePath);
                return;
            }

            logger.info("Indexing file: {}", filePath);
            String content = Files.readString(path);

            List<Symbol> symbols = null;
            if (filePath.toLowerCase().endsWith(".java")) {
                symbols = extractSymbols(content, path);
            }

            // Update metadata and symbols in a single transaction
            saveFileData(projectId, filePath, checksum, fileSize, now, symbols);

            // Index content in Lucene (Outside DB transaction)
            luceneIndexService.indexFileContent(projectId, filePath, content);

        } catch (Throwable t) {
            logger.error("Critical error indexing file: {}", path, t);
        }
    }

    @Transactional
    protected void saveFileData(Long projectId, String filePath, String checksum, long fileSize, LocalDateTime now,
            List<Symbol> symbols) {
        // Clear existing symbols
        symbolRepository.deleteByProjectIdAndFilePath(projectId, filePath);
        symbolCache.invalidate(projectId + ":" + filePath);

        if (symbols != null && !symbols.isEmpty()) {
            for (Symbol s : symbols) {
                s.setProjectId(projectId);
                s.setFilePath(filePath);
                s.setLastModified(now);
            }
            symbolRepository.saveAll(symbols);
            symbolCache.put(projectId + ":" + filePath, symbols);
        }

        FileMetadataId id = new FileMetadataId(projectId, filePath);
        FileMetadata metadata = fileMetadataRepository.findById(id).orElse(new FileMetadata());
        if (metadata.getProjectId() == null) {
            metadata.setProjectId(projectId);
            metadata.setFilePath(filePath);
        }
        metadata.setChecksum(checksum);
        metadata.setFileSize(fileSize);
        metadata.setLastScanned(now);
        fileMetadataRepository.save(metadata);
    }

    public List<Symbol> getSymbols(Long projectId, String filePath) {
        return symbolCache.get(projectId + ":" + filePath, k -> {
            return symbolRepository.findByProjectIdAndFilePath(projectId, filePath);
        });
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

    private List<Symbol> extractSymbols(String content, Path path) {
        List<Symbol> symbols = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(content);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                Symbol s = new Symbol();
                s.setName(c.getNameAsString());
                s.setType("CLASS");
                symbols.add(s);
            });
            cu.findAll(MethodDeclaration.class).forEach(m -> {
                Symbol s = new Symbol();
                s.setName(m.getNameAsString());
                s.setType("METHOD");
                symbols.add(s);
            });
            cu.findAll(FieldDeclaration.class).forEach(f -> {
                f.getVariables().forEach(v -> {
                    Symbol s = new Symbol();
                    s.setName(v.getNameAsString());
                    s.setType("FIELD");
                    symbols.add(s);
                });
            });

            logger.info("Extracted {} symbols from file: {}", symbols.size(), path);
        } catch (Throwable t) {
            logger.error("StaticJavaParser failed for file: {}", path, t);
        }

        return symbols;
    }

    private String computeChecksum(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path);
                DigestInputStream dis = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // Read through the stream to update the digest
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
