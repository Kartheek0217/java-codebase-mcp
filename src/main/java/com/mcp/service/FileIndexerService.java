package com.mcp.service;

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
import java.nio.file.Files;
import java.nio.file.Path;
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

    public FileIndexerService(SymbolRepository symbolRepository, 
                              FileMetadataRepository fileMetadataRepository,
                              LuceneIndexService luceneIndexService) {
        this.symbolRepository = symbolRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.luceneIndexService = luceneIndexService;
        
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17); 
        StaticJavaParser.setConfiguration(config);
    }

    @Transactional
    public void indexFile(Long projectId, Path path) {
        logger.debug("indexFile called for project {} and path {}", projectId, path);
        try {
            String filePath = path.toAbsolutePath().toString();
            String checksum = computeChecksum(path);
            LocalDateTime now = LocalDateTime.now();

            FileMetadataId id = new FileMetadataId(projectId, filePath);
            FileMetadata metadata = fileMetadataRepository.findById(id).orElse(null);
            
            if (metadata != null && metadata.getChecksum().equals(checksum)) {
                logger.debug("File unchanged: {}", filePath);
                return;
            }

            logger.info("Indexing file: {}", filePath);
            String content = Files.readString(path);
            
            // Clear existing data
            symbolRepository.deleteByProjectIdAndFilePath(projectId, filePath);

            // Parse symbols ONLY for Java files
            if (filePath.toLowerCase().endsWith(".java")) {
                List<Symbol> symbols = extractSymbols(content, path);
                for (Symbol s : symbols) {
                    s.setProjectId(projectId);
                    s.setFilePath(filePath);
                    s.setLastModified(now);
                }
                symbolRepository.saveAll(symbols);
            }

            // Index content in Lucene (for ALL supported files)
            luceneIndexService.indexFileContent(projectId, filePath, content);

            // Update metadata
            if (metadata == null) {
                metadata = new FileMetadata();
                metadata.setProjectId(projectId);
                metadata.setFilePath(filePath);
            }
            metadata.setChecksum(checksum);
            metadata.setLastScanned(now);
            fileMetadataRepository.save(metadata);

        } catch (Throwable t) {
            logger.error("Critical error indexing file: {}", path, t);
        }
    }

    @Transactional
    public void deleteFileData(Long projectId, Path path) {
        String filePath = path.toAbsolutePath().toString();
        logger.info("Deleting data for file: {} in project {}", filePath, projectId);
        symbolRepository.deleteByProjectIdAndFilePath(projectId, filePath);
        fileMetadataRepository.deleteById(new FileMetadataId(projectId, filePath));
        luceneIndexService.deleteFileContent(projectId, filePath);
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
        byte[] bytes = Files.readAllBytes(path);
        byte[] hash = digest.digest(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
