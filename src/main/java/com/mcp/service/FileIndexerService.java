package com.mcp.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolType;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SymbolRepository;

@Service
public class FileIndexerService {

	private static final Logger logger = LoggerFactory.getLogger(FileIndexerService.class);
	private static final Pattern JS_PATTERN = Pattern.compile(
			"(?:\\bfunction\\s+([a-zA-Z0-9_$]+))|(?:\\bclass\\s+([a-zA-Z0-9_$]+))|(?:\\b(?:const|let|var)\\s+([a-zA-Z0-9_$]+)\\s*=\\s*(?:async\\s+)?(?:\\([^)]*\\)|[a-zA-Z0-9_$]+)\\s*=>)");
	private static final Pattern JSON_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:");
	private static final Pattern ID_PATTERN = Pattern.compile("id=[\"']([^\"']+)[\"']");
	private static final Pattern CSS_PATTERN = Pattern.compile("(?:^|\\s)([.#][a-zA-Z0-9_-]+)(?=\\s*\\{)");

	private final SymbolRepository symbolRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final LuceneIndexService luceneIndexService;
	private final Cache<String, List<Symbol>> symbolCache;
	private final SkillService skillService;

	public FileIndexerService(SymbolRepository symbolRepository, FileMetadataRepository fileMetadataRepository,
			LuceneIndexService luceneIndexService, Cache<String, List<Symbol>> symbolCache, SkillService skillService) {
		this.symbolRepository = symbolRepository;
		this.fileMetadataRepository = fileMetadataRepository;
		this.luceneIndexService = luceneIndexService;
		this.symbolCache = symbolCache;
		this.skillService = skillService;

		ParserConfiguration config = new ParserConfiguration();
		config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
		StaticJavaParser.setConfiguration(config);
	}

	public LuceneIndexService getLuceneIndexService() {
		return luceneIndexService;
	}

	public SymbolRepository getSymbolRepository() {
		return symbolRepository;
	}

	public void indexFile(Long projectId, Path path) {
		logger.debug("indexFile called for project {} and path {}", projectId, path);
		try {
			String filePath = path.toAbsolutePath().toString();
			FileMetadataId id = new FileMetadataId(projectId, filePath);
			FileMetadata metadata = fileMetadataRepository.findById(id).orElse(null);

			// Optimization: Read file once and compute checksum while reading
			ReadResult result = readFileAndChecksum(path);
			String checksum = result.checksum;
			String content = result.content;
			long fileSize = result.fileSize;
			LocalDateTime now = LocalDateTime.now();

			if (metadata != null && metadata.getChecksum().equals(checksum)) {
				logger.debug("File unchanged: {}", filePath);
				return;
			}

			logger.info("Indexing file: {}", filePath);

			List<Symbol> symbols = null;
			if (filePath.toLowerCase().endsWith(".java")) {
				symbols = extractSymbols(content, path);
			} else if (filePath.toLowerCase().endsWith(".md")) {
				skillService.learnSkillFromMarkdown(projectId, content, filePath);
			} else {
				symbols = extractGeneralSymbols(content, filePath);
			}

			// Update metadata and symbols in a single transaction (Reuse existing metadata
			// object)
			saveFileData(projectId, filePath, checksum, fileSize, now, symbols, metadata);

			// Index content in Lucene (Outside DB transaction)
			luceneIndexService.indexFileContent(projectId, filePath, content);

		} catch (Throwable t) {
			logger.error("Critical error indexing file: {}", path, t);
		}
	}

	@Transactional
	protected void saveFileData(Long projectId, String filePath, String checksum, long fileSize, LocalDateTime now,
			List<Symbol> symbols, FileMetadata existingMetadata) {
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

		FileMetadata metadata = existingMetadata;
		if (metadata == null) {
			FileMetadataId id = new FileMetadataId(projectId, filePath);
			metadata = fileMetadataRepository.findById(id).orElse(new FileMetadata());
		}

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

	private List<Symbol> extractGeneralSymbols(String content, String filePath) {
		List<Symbol> symbols = new ArrayList<>();
		String lowerPath = filePath.toLowerCase();

		if (lowerPath.endsWith(".js") || lowerPath.endsWith(".jsx") || lowerPath.endsWith(".ts")
				|| lowerPath.endsWith(".tsx") || lowerPath.endsWith(".vue")) {
			// JS/TS/Vue Scripts: Functions, Classes, and Arrow Functions
			Matcher matcher = JS_PATTERN.matcher(content);
			while (matcher.find()) {
				if (matcher.group(1) != null) {
					addSymbol(symbols, matcher.group(1), SymbolType.FUNCTION);
				} else if (matcher.group(2) != null) {
					addSymbol(symbols, matcher.group(2), SymbolType.CLASS);
				} else if (matcher.group(3) != null) {
					addSymbol(symbols, matcher.group(3), SymbolType.ARROW_FUNCTION);
				}
			}
		}

		if (lowerPath.endsWith(".json")) {
			// JSON: Keys
			Matcher matcher = JSON_PATTERN.matcher(content);
			while (matcher.find()) {
				addSymbol(symbols, matcher.group(1), SymbolType.JSON_KEY);
			}
		}

		if (lowerPath.endsWith(".html") || lowerPath.endsWith(".vue")) {
			// HTML/Vue Templates: IDs
			Matcher matcher = ID_PATTERN.matcher(content);
			while (matcher.find()) {
				addSymbol(symbols, matcher.group(1), SymbolType.ID);
			}
		}

		if (lowerPath.endsWith(".css") || lowerPath.endsWith(".vue")) {
			// CSS/Vue Styles: Classes and IDs
			Matcher matcher = CSS_PATTERN.matcher(content);
			while (matcher.find()) {
				String selector = matcher.group(1);
				SymbolType type = selector.startsWith(".") ? SymbolType.CSS_CLASS : SymbolType.CSS_ID;
				addSymbol(symbols, selector.substring(1), type);
			}
		}

		return symbols;
	}

	private void addSymbol(List<Symbol> symbols, String name, SymbolType type) {
		Symbol s = new Symbol();
		s.setName(name);
		s.setType(type);
		symbols.add(s);
	}

	private List<Symbol> extractSymbols(String content, Path path) {
		List<Symbol> symbols = new ArrayList<>();
		try {
			CompilationUnit cu = StaticJavaParser.parse(content);

			// Optimization: Use a single pass visitor to extract all symbols
			cu.accept(new VoidVisitorAdapter<List<Symbol>>() {
				@Override
				public void visit(ClassOrInterfaceDeclaration n, List<Symbol> arg) {
					arg.add(createSymbol(n.getNameAsString(), SymbolType.CLASS));
					super.visit(n, arg);
				}

				@Override
				public void visit(MethodDeclaration n, List<Symbol> arg) {
					arg.add(createSymbol(n.getNameAsString(), SymbolType.METHOD));
					super.visit(n, arg);
				}

				@Override
				public void visit(FieldDeclaration n, List<Symbol> arg) {
					n.getVariables().forEach(v -> arg.add(createSymbol(v.getNameAsString(), SymbolType.FIELD)));
					super.visit(n, arg);
				}
			}, symbols);

			logger.info("Extracted {} symbols from file: {}", symbols.size(), path);
		} catch (Throwable t) {
			logger.error("StaticJavaParser failed for file: {}", path, t);
		}

		return symbols;
	}

	private Symbol createSymbol(String name, SymbolType type) {
		Symbol s = new Symbol();
		s.setName(name);
		s.setType(type);
		return s;
	}

	private String extractPdfText(Path path) {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(document);
		} catch (IOException e) {
			logger.error("Failed to extract text from PDF: {}", path, e);
			return "";
		}
	}

	private record ReadResult(String content, String checksum, long fileSize) {
	}

	private ReadResult readFileAndChecksum(Path path) throws IOException, NoSuchAlgorithmException {
		String filePath = path.toString().toLowerCase();
		if (filePath.endsWith(".pdf")) {
			return new ReadResult(extractPdfText(path), computeChecksum(path), Files.size(path));
		}

		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		// Optimization: Pre-calculate the result size to avoid unnecessary growth of
		// ByteArrayOutputStream
		long size = Files.size(path);
		int initialSize = size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;

		try (InputStream is = Files.newInputStream(path);
				DigestInputStream dis = new DigestInputStream(new BufferedInputStream(is), digest);
				ByteArrayOutputStream bos = new ByteArrayOutputStream(initialSize)) {

			byte[] buffer = new byte[8192];
			int read;
			while ((read = dis.read(buffer)) != -1) {
				bos.write(buffer, 0, read);
			}

			byte[] hash = digest.digest();
			String checksum = bytesToHex(hash);
			String content = bos.toString(StandardCharsets.UTF_8);

			return new ReadResult(content, checksum, bos.size());
		}
	}

	private String bytesToHex(byte[] hash) {
		StringBuilder hexString = new StringBuilder();
		for (byte b : hash) {
			hexString.append(String.format("%02x", b));
		}
		return hexString.toString();
	}

	private String computeChecksum(Path path) throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream is = Files.newInputStream(path); DigestInputStream dis = new DigestInputStream(is, digest)) {
			byte[] buffer = new byte[8192];
			while (dis.read(buffer) != -1) {
			}
		}
		byte[] hash = digest.digest();
		StringBuilder hexString = new StringBuilder();
		for (byte b : hash) {
			hexString.append(String.format("%02x", b));
		}
		return hexString.toString();
	}
}
