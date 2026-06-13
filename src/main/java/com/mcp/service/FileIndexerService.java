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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolType;
import com.mcp.event.MarkdownFileIndexedEvent;
import com.mcp.repository.FileMetadataRepository;
import com.mcp.repository.SymbolRepository;

@Service
public class FileIndexerService {

	private static final Logger logger = LoggerFactory.getLogger(FileIndexerService.class);
	private static final Pattern JS_PATTERN = Pattern.compile(
			"(?:\\b(?:async\\s+)?function\\s+([a-zA-Z0-9_$]+))|" +
					"(?:\\bclass\\s+([a-zA-Z0-9_$]+))|" +
					"(?:\\binterface\\s+([a-zA-Z0-9_$]+))|" +
					"(?:\\btype\\s+([a-zA-Z0-9_$]+)\\s*=)|" +
					"(?:\\b(?:const|let|var)\\s+([a-zA-Z0-9_$]+)\\s*=\\s*(?:async\\s+)?(?:\\([^)]*\\)|[a-zA-Z0-9_$]+)\\s*=>)");
	private static final Pattern JSON_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:");
	private static final Pattern ID_PATTERN = Pattern.compile("id=[\"']([^\"']+)[\"']");
	private static final Pattern CSS_PATTERN = Pattern.compile("(?:^|\\s|,)([.#][a-zA-Z0-9_-]+)(?=\\s*[,{])");

	private final SymbolRepository symbolRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final LuceneIndexService luceneIndexService;
	private final Cache<String, List<Symbol>> symbolCache;
	private final ApplicationEventPublisher eventPublisher;
	private final FileDataPersistenceService fileDataPersistenceService;

	public FileIndexerService(SymbolRepository symbolRepository, FileMetadataRepository fileMetadataRepository,
			LuceneIndexService luceneIndexService, Cache<String, List<Symbol>> symbolCache,
			ApplicationEventPublisher eventPublisher,
			FileDataPersistenceService fileDataPersistenceService) {
		this.symbolRepository = symbolRepository;
		this.fileMetadataRepository = fileMetadataRepository;
		this.luceneIndexService = luceneIndexService;
		this.symbolCache = symbolCache;
		this.eventPublisher = eventPublisher;
		this.fileDataPersistenceService = fileDataPersistenceService;
	}



	private static final ParserConfiguration JAVA_PARSER_CONFIG = new ParserConfiguration()
			// Fix I: BLEEDING_EDGE handles JDK 25 syntax (unnamed vars, pattern matching
			// extensions) that JAVA_21 would silently fail to parse
			.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

	private JavaParser createJavaParser() {
		return new JavaParser(JAVA_PARSER_CONFIG);
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
			long currentFileSize = Files.size(path);
			if (currentFileSize > 500_000) {
				logger.warn("Skipping file {} as its size ({} bytes) exceeds the 500KB limit.", path, currentFileSize);
				return;
			}

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
			List<CallInfo> calls = null;
			String dependencies = null;
			if (filePath.toLowerCase().endsWith(".java")) {
				JavaAnalysisResult javaResult = extractJavaData(content, path);
				symbols = javaResult.symbols;
				calls = javaResult.calls;
				dependencies = javaResult.dependencies;
			} else if (filePath.toLowerCase().endsWith(".md")) {
				// Decouple: publish event instead of calling SkillService directly
				eventPublisher.publishEvent(new MarkdownFileIndexedEvent(this, projectId, content, filePath));
				// Persist metadata so checksum is stored and the file is not re-processed on
				// every scan (symbols/calls are null — skills are stored separately)
			} else {
				symbols = extractGeneralSymbols(content, filePath);
			}

			fileDataPersistenceService.saveFileData(projectId, filePath, checksum, fileSize, now, symbols, calls, dependencies, metadata);

			// Index content in Lucene (Outside DB transaction)
			luceneIndexService.indexFileContent(projectId, filePath, content);

		} catch (Exception e) {
			logger.error("Failed to index file {} in project {}: {}", path, projectId, e.getMessage(), e);
		}
	}



	public List<Symbol> getSymbols(Long projectId, String filePath) {
		return symbolCache.get(projectId + ":" + filePath, k -> {
			return symbolRepository.findByProjectIdAndFilePath(projectId, filePath);
		});
	}

	public void deleteFileData(Long projectId, Path path) {
		fileDataPersistenceService.deleteFileData(projectId, path);
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
					addSymbol(symbols, matcher.group(3), SymbolType.INTERFACE);
				} else if (matcher.group(4) != null) {
					addSymbol(symbols, matcher.group(4), SymbolType.TYPE);
				} else if (matcher.group(5) != null) {
					addSymbol(symbols, matcher.group(5), SymbolType.ARROW_FUNCTION);
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

	public record CallInfo(String callerName, String calleeName) {
	}

	private record JavaAnalysisResult(List<Symbol> symbols, List<CallInfo> calls, String dependencies) {
	}

	private JavaAnalysisResult extractJavaData(String content, Path path) {
		List<Symbol> symbols = new ArrayList<>();
		List<CallInfo> calls = new ArrayList<>();
		StringBuilder dependencies = new StringBuilder();
		try {
			JavaParser javaParser = createJavaParser();
			ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
			if (!parseResult.isSuccessful()) {
				logger.error("JavaParser failed for file: {}. Problems: {}", path,
						parseResult.getProblems().stream().map(Object::toString).toList());
				return new JavaAnalysisResult(new ArrayList<>(), new ArrayList<>(), "");
			}
			CompilationUnit cu = parseResult.getResult().get();

			// Extract imports
			cu.getImports().forEach(i -> {
				if (dependencies.length() > 0)
					dependencies.append(",");
				dependencies.append(i.getNameAsString());
			});

			// Optimization: Use a single pass visitor to extract all symbols and calls
			cu.accept(new VoidVisitorAdapter<Object>() {
				private String currentMethod = null;

				@Override
				public void visit(ClassOrInterfaceDeclaration n, Object arg) {
					Symbol s = createSymbol(n.getNameAsString(), SymbolType.CLASS);
					// Fix E+F: capture line number and annotations for class symbols
					n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
					s.setModifiers(n.getModifiers().stream()
							.map(m -> m.getKeyword().asString()).collect(Collectors.joining(" ")));
					s.setAnnotations(n.getAnnotations().stream()
							.map(a -> "@" + a.getNameAsString()).collect(Collectors.joining(" ")));
					symbols.add(s);
					super.visit(n, arg);
				}

				@Override
				public void visit(MethodDeclaration n, Object arg) {
					String oldMethod = currentMethod;
					currentMethod = n.getNameAsString();
					Symbol s = createSymbol(currentMethod, SymbolType.METHOD);
					// Fix E+F: capture full method metadata for AI navigation
					n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
					try {
						s.setSignature(n.getDeclarationAsString(false, false, true));
					} catch (Exception e) {
						// Defensive: some malformed AST nodes fail declaration serialisation
						logger.trace("Could not get declaration string for method {} in {}: {}",
								n.getNameAsString(), path, e.getMessage());
					}
					s.setReturnType(n.getTypeAsString());
					s.setModifiers(n.getModifiers().stream()
							.map(m -> m.getKeyword().asString()).collect(Collectors.joining(" ")));
					s.setAnnotations(n.getAnnotations().stream()
							.map(a -> "@" + a.getNameAsString()).collect(Collectors.joining(" ")));
					symbols.add(s);
					super.visit(n, arg);
					currentMethod = oldMethod;
				}

				@Override
				public void visit(RecordDeclaration n, Object arg) {
					Symbol s = createSymbol(n.getNameAsString(), SymbolType.CLASS);
					n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
					s.setAnnotations(n.getAnnotations().stream()
							.map(a -> "@" + a.getNameAsString()).collect(Collectors.joining(" ")));
					symbols.add(s);
					super.visit(n, arg);
				}

				@Override
				public void visit(EnumDeclaration n, Object arg) {
					Symbol s = createSymbol(n.getNameAsString(), SymbolType.CLASS);
					n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
					s.setAnnotations(n.getAnnotations().stream()
							.map(a -> "@" + a.getNameAsString()).collect(Collectors.joining(" ")));
					symbols.add(s);
					super.visit(n, arg);
				}

				@Override
				public void visit(AnnotationDeclaration n, Object arg) {
					Symbol s = createSymbol(n.getNameAsString(), SymbolType.CLASS);
					n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
					symbols.add(s);
					super.visit(n, arg);
				}

				@Override
				public void visit(MethodCallExpr n, Object arg) {
					if (currentMethod != null) {
						calls.add(new CallInfo(currentMethod, n.getNameAsString()));
					}
					super.visit(n, arg);
				}

				@Override
				public void visit(FieldDeclaration n, Object arg) {
					String mods = n.getModifiers().stream()
							.map(m -> m.getKeyword().asString()).collect(Collectors.joining(" "));
					String annots = n.getAnnotations().stream()
							.map(a -> "@" + a.getNameAsString()).collect(Collectors.joining(" "));
					n.getVariables().forEach(v -> {
						Symbol s = createSymbol(v.getNameAsString(), SymbolType.FIELD);
						n.getBegin().ifPresent(pos -> s.setLineNumber(pos.line));
						s.setReturnType(n.getElementType().asString());
						s.setModifiers(mods);
						s.setAnnotations(annots);
						symbols.add(s);
					});
					super.visit(n, arg);
				}
			}, null);

			logger.info("Extracted {} symbols, {} calls, and {} imports from file: {}",
					symbols.size(), calls.size(), cu.getImports().size(), path);
			if (symbols.isEmpty()) {
				logger.warn("No symbols extracted from Java file: {}", path);
			}
		} catch (Throwable t) {
			logger.error("StaticJavaParser failed for file: {}. Error: {}", path, t.getMessage(), t);
		}

		return new JavaAnalysisResult(symbols, calls, dependencies.toString());
	}

	private Symbol createSymbol(String name, SymbolType type) {
		Symbol s = new Symbol();
		s.setName(name);
		s.setType(type);
		return s;
	}

	private record ReadResult(String content, String checksum, long fileSize) {
	}

	private ReadResult readFileAndChecksum(Path path) throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		// Optimization: Pre-calculate the result size to avoid unnecessary growth of
		// ByteArrayOutputStream
		long size = Files.size(path);
		int initialSize = (size > 0 && size <= Integer.MAX_VALUE) ? (int) size : 8192;

		try (InputStream is = Files.newInputStream(path);
				DigestInputStream dis = new DigestInputStream(new BufferedInputStream(is), digest);
				ByteArrayOutputStream bos = new ByteArrayOutputStream(initialSize)) {

			byte[] buffer = new byte[8192];
			int read;
			while ((read = dis.read(buffer)) != -1) {
				bos.write(buffer, 0, read);
			}

			byte[] hash = digest.digest();
			String checksum = java.util.HexFormat.of().formatHex(hash);
			String content = bos.toString(StandardCharsets.UTF_8);

			return new ReadResult(content, checksum, bos.size());
		}
	}
}
