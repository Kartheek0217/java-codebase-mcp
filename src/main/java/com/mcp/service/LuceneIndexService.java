package com.mcp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mcp.analysis.CodeAnalyzer;
import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.SearchOptions;
import com.mcp.properties.LuceneProperties;

import jakarta.annotation.PreDestroy;

@Service
public class LuceneIndexService {

	private static final Logger logger = LoggerFactory.getLogger(LuceneIndexService.class);
	// Resolve to absolute path at class-load time so the index location is stable
	// regardless of which directory the JVM is launched from.
	@org.springframework.beans.factory.annotation.Value("${lucene.indexDir:${user.dir}/data/indices}")
	private String baseIndexDir;

	@org.springframework.beans.factory.annotation.Value("${lucene.searchTimeoutMs:2000}")
	private long searchTimeoutMs;

	private final Map<Long, IndexWriter> writers = new ConcurrentHashMap<>();
	private final Map<Long, SearcherManager> searcherManagers = new ConcurrentHashMap<>();
	private final Set<Long> bulkModeProjects = ConcurrentHashMap.newKeySet();
	private final Analyzer sharedAnalyzer = new CodeAnalyzer();

	private final LuceneProperties luceneProperties;
	private final ReentrantLock writerInitializationLock = new ReentrantLock();

	public LuceneIndexService(LuceneProperties luceneProperties) {
		this.luceneProperties = luceneProperties;
	}

	public void setBulkMode(Long projectId, boolean enabled) {
		if (enabled) {
			bulkModeProjects.add(projectId);
		} else {
			bulkModeProjects.remove(projectId);
			try {
				commitAndRefresh(projectId);
			} catch (IOException e) {
				logger.error("Error during final commit/refresh for project {}", projectId, e);
			}
		}
	}

	public void commitAndRefresh(Long projectId) throws IOException {
		IndexWriter writer = writers.get(projectId);
		if (writer != null) {
			writer.commit();
			SearcherManager sm = searcherManagers.get(projectId);
			if (sm != null) {
				sm.maybeRefreshBlocking();
			}
		}
	}

	@Scheduled(fixedDelay = 5000)
	public void scheduleCommits() {
		writers.forEach((projectId, writer) -> {
			try {
				if (writer.isOpen() && writer.hasUncommittedChanges()) {
					logger.debug("Performing scheduled commit for project {}", projectId);
					writer.commit();
					// Fix H: refresh SearcherManager so searches see the newly-committed data
					SearcherManager sm = searcherManagers.get(projectId);
					if (sm != null) {
						sm.maybeRefresh();
					}
				}
			} catch (AlreadyClosedException ignored) {
				// writer was concurrently deleted — safe to skip
			} catch (IOException e) {
				logger.error("Error during scheduled commit for project {}", projectId, e);
			}
		});
	}

	private IndexWriter getWriter(Long projectId) throws IOException {
		// Avoid computeIfAbsent with a blocking IO lambda — ConcurrentHashMap docs warn
		// this can deadlock when the mapping function performs blocking operations.
		IndexWriter existing = writers.get(projectId);
		if (existing != null) {
			return existing;
		}
		// synchronized(this) is coarse-grained (serialises all projects' first write),
		// but writer creation is a one-time-per-project event so contention is negligible.
		writerInitializationLock.lock();
		try {
			// Double-check inside the lock
			existing = writers.get(projectId);
			if (existing != null) {
				return existing;
			}
			try {
				Path indexPath = Paths.get(baseIndexDir, String.valueOf(projectId));
				if (!Files.exists(indexPath)) {
					Files.createDirectories(indexPath);
				}
				Directory directory = FSDirectory.open(indexPath);
				IndexWriterConfig config = new IndexWriterConfig(sharedAnalyzer);
				config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
				// Optimization: Use configurable RAM buffer
				config.setRAMBufferSizeMB(luceneProperties.getBufferSize());
				IndexWriter writer = new IndexWriter(directory, config);

				SearcherManager sm = new SearcherManager(writer, true, true, new SearcherFactory());
				searcherManagers.put(projectId, sm);
				writers.put(projectId, writer);
				return writer;
			} catch (IOException e) {
				logger.error("Error creating IndexWriter for project {}", projectId, e);
				throw e;
			}
		} finally {
			writerInitializationLock.unlock();
		}
	}

	private SearcherManager getSearcherManager(Long projectId) throws IOException {
		SearcherManager sm = searcherManagers.get(projectId);
		if (sm == null) {
			// Trigger writer creation which also creates SearcherManager
			getWriter(projectId);
			sm = searcherManagers.get(projectId);
		}
		return sm;
	}

	public void indexFileContent(Long projectId, String filePath, String content) {
		indexFileContent(projectId, filePath, content, "code");
	}

	public void indexFileContent(Long projectId, String filePath, String content, String type) {
		indexFileContent(projectId, filePath, null, content, type);
	}

	public void indexFileContent(Long projectId, String filePath, String title, String content, String type) {
		try {
			IndexWriter writer = getWriter(projectId);
			Document doc = new Document();
			doc.add(new StringField("path", filePath, Field.Store.YES));
			if (title != null) {
				doc.add(new TextField("title", title, Field.Store.YES));
			}
			doc.add(new TextField("content", content, Field.Store.NO));
			doc.add(new StringField("type", type, Field.Store.YES));

			if ("web".equals(type)) {
				try {
					java.net.URI uri = new java.net.URI(filePath);
					String host = uri.getHost();
					if (host != null) {
						doc.add(new StringField("domain", host, Field.Store.YES));
					}
				} catch (Exception e) {
					// Ignore
				}
			}

			writer.updateDocument(new Term("path", filePath), doc);

			// Use Near-Real-Time (NRT) refresh instead of full commit for better
			// performance
			SearcherManager sm = searcherManagers.get(projectId);
			if (sm != null) {
				if (!bulkModeProjects.contains(projectId)) {
					sm.maybeRefresh();
				}
			}
		} catch (IOException e) {
			logger.error("Error indexing {} content for file: {}", type, filePath, e);
		}
	}

	public void deleteFileContent(Long projectId, String filePath) {
		try {
			IndexWriter writer = writers.get(projectId);
			if (writer != null) {
				writer.deleteDocuments(new Term("path", filePath));
				SearcherManager sm = searcherManagers.get(projectId);
				if (sm != null) {
					sm.maybeRefresh();
				}
			}
		} catch (IOException e) {
			logger.error("Error deleting content for file: {}", filePath, e);
		}
	}

	// ─── Canonical search entry-point ────────────────────────────────────────

	/**
	 * @implNote Single canonical entry-point for all Lucene content searches.
	 *           All overloads delegate here via {@link SearchOptions}.
	 * @param projectId Project to search within
	 * @param opts      Fully-specified search options
	 * @return List of content search results
	 */
	public List<ContentSearchResult> searchContent(Long projectId, SearchOptions opts) {
		return searchContent(projectId, opts.query(), opts.type(), opts.site(),
				opts.filePaths(), opts.limit(), opts.offset());
	}

	// ─── Backward-compatible overload shims ───────────────────────────────────

	/** @deprecated Use {@link #searchContent(Long, SearchOptions)} */
	public List<ContentSearchResult> searchContent(Long projectId, String queryStr) {
		return searchContent(projectId, SearchOptions.builder().query(queryStr).limit(50).build());
	}

	/** @deprecated Use {@link #searchContent(Long, SearchOptions)} */
	public List<ContentSearchResult> searchContent(Long projectId, String queryStr, int limit) {
		return searchContent(projectId, SearchOptions.builder().query(queryStr).limit(limit).build());
	}

	/** @deprecated Use {@link #searchContent(Long, SearchOptions)} */
	public List<ContentSearchResult> searchContent(Long projectId, String queryStr, String type, int limit,
			int offset) {
		return searchContent(projectId, SearchOptions.builder().query(queryStr).type(type).limit(limit).offset(offset).build());
	}

	/** @deprecated Use {@link #searchContent(Long, SearchOptions)} */
	public List<ContentSearchResult> searchContent(Long projectId, String queryStr, Set<String> filePaths, int limit) {
		return searchContent(projectId, SearchOptions.builder().query(queryStr).filePaths(filePaths).limit(limit).build());
	}

	/** @deprecated Use {@link #searchContent(Long, SearchOptions)} */
	public List<ContentSearchResult> searchContent(Long projectId, String queryStr, String type, String site, int limit,
			int offset) {
		return searchContent(projectId, SearchOptions.builder().query(queryStr).type(type).site(site).limit(limit).offset(offset).build());
	}

	// ─── Core implementation ──────────────────────────────────────────────────
	private List<ContentSearchResult> searchContent(Long projectId, String queryStr, String type, String site,
			Set<String> filePaths, int limit, int offset) {
		List<ContentSearchResult> results = new ArrayList<>();
		try {
			SearcherManager sm = getSearcherManager(projectId);
			if (sm == null)
				return results;

			IndexSearcher searcher = sm.acquire();
			try {
				QueryParser parser = new QueryParser("content", sharedAnalyzer);
				parser.setAllowLeadingWildcard(true);
				parser.setDefaultOperator(QueryParser.Operator.AND);

				Query query;
				try {
					query = parser.parse(queryStr);
				} catch (org.apache.lucene.queryparser.classic.ParseException e) {
					query = parser.parse(QueryParser.escape(queryStr));
				}

				// Apply filters if provided
				if (type != null || site != null || (filePaths != null && !filePaths.isEmpty())) {
					org.apache.lucene.search.BooleanQuery.Builder builder = new org.apache.lucene.search.BooleanQuery.Builder();
					builder.add(query, org.apache.lucene.search.BooleanClause.Occur.MUST);

					if (type != null) {
						builder.add(new org.apache.lucene.search.TermQuery(new Term("type", type)),
								org.apache.lucene.search.BooleanClause.Occur.MUST);
					}

					if (site != null && !site.isEmpty()) {
						org.apache.lucene.search.BooleanQuery.Builder siteBuilder = new org.apache.lucene.search.BooleanQuery.Builder();
						siteBuilder.add(new org.apache.lucene.search.TermQuery(new Term("domain", site)),
								org.apache.lucene.search.BooleanClause.Occur.SHOULD);
						siteBuilder.add(new org.apache.lucene.search.WildcardQuery(new Term("path", "*" + site + "*")),
								org.apache.lucene.search.BooleanClause.Occur.SHOULD);
						builder.add(siteBuilder.build(), org.apache.lucene.search.BooleanClause.Occur.MUST);
					}

					if (filePaths != null && !filePaths.isEmpty()) {
						List<org.apache.lucene.util.BytesRef> bytesRefs = filePaths.stream()
								.map(org.apache.lucene.util.BytesRef::new)
								.toList();
						builder.add(new org.apache.lucene.search.TermInSetQuery("path", bytesRefs),
								org.apache.lucene.search.BooleanClause.Occur.MUST);
					}

					query = builder.build();
				}

				searcher.setTimeout(new IndexSearcherTimeout(searchTimeoutMs));

				TopDocs topDocs = searcher.search(query, limit + offset);

				if (topDocs.scoreDocs.length > offset) {
					UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, sharedAnalyzer).build();
					String[] snippets = highlighter.highlight("content", query, topDocs, 5);

					StoredFields storedFields = searcher.storedFields();
					for (int i = offset; i < topDocs.scoreDocs.length; i++) {
						ScoreDoc scoreDoc = topDocs.scoreDocs[i];
						Document doc = storedFields.document(scoreDoc.doc);
						String filePath = doc.get("path");
						String title = doc.get("title");
						String content = doc.get("content");
						if (content == null && filePath != null) {
							try {
								content = Files.readString(Paths.get(filePath));
							} catch (IOException e) {
								content = "";
							}
						}
						String snippet = (snippets != null && i < snippets.length) ? snippets[i] : "";

						if (snippet != null && !snippet.isEmpty()) {
							List<ContentSearchResult.ContentMatch> matches = parseSnippets(snippet, content);
							results.add(new ContentSearchResult(filePath, title, scoreDoc.score, matches));
						}
					}
				}
			} finally {
				sm.release(searcher);
			}
		} catch (AlreadyClosedException | CorruptIndexException e) {
			logger.error("Index unavailable for project {}", projectId, e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Search index temporarily unavailable");
		} catch (Exception e) {
			logger.error("Search failed for project {}: {}", projectId, queryStr, e);
		}
		return results;
	}

	public boolean verifyIndex(Long projectId) {
		try {
			SearcherManager sm = getSearcherManager(projectId);
			if (sm == null)
				return false;
			IndexSearcher searcher = sm.acquire();
			try {
				return searcher.getIndexReader().numDocs() >= 0;
			} finally {
				sm.release(searcher);
			}
		} catch (Exception e) {
			logger.warn("Index verification failed for project {}: {}", projectId, e.getMessage());
			return false;
		}
	}

	private List<ContentSearchResult.ContentMatch> parseSnippets(String snippet, String fileContent) {
		List<ContentSearchResult.ContentMatch> matches = new ArrayList<>();
		// Fixed: was `fileContent.isEmpty()` which nulled out valid content and caused
		// line numbers to always return 0. Now correctly preserves non-empty content.
		if (fileContent == null || fileContent.isEmpty()) {
			fileContent = null;
		}

		String[] passages = snippet.split("(?i)<b>...</b>|\\.\\.\\.");

		for (String passage : passages) {
			String cleanPassage = passage.replaceAll("<b>|</b>", "").trim();
			if (cleanPassage.isEmpty())
				continue;

			int lineNumber = 0;
			if (fileContent != null) {
				int index = fileContent.indexOf(cleanPassage);
				if (index != -1) {
					lineNumber = 1;
					for (int i = 0; i < index; i++) {
						if (fileContent.charAt(i) == '\n')
							lineNumber++;
					}
				}
			}

			matches.add(new ContentSearchResult.ContentMatch(lineNumber, cleanPassage, "unknown", 0, 0));
		}
		return matches;
	}

	private static class IndexSearcherTimeout implements QueryTimeout {
		private final long timeoutAt;

		public IndexSearcherTimeout(long timeoutMs) {
			this.timeoutAt = System.currentTimeMillis() + timeoutMs;
		}

		@Override
		public boolean shouldExit() {
			return System.currentTimeMillis() > timeoutAt;
		}
	}

	public void deleteIndex(Long projectId) {
		try {
			SearcherManager sm = searcherManagers.remove(projectId);
			if (sm != null) {
				sm.close();
			}
			IndexWriter writer = writers.remove(projectId);
			if (writer != null) {
				writer.close();
			}
			Path indexPath = Paths.get(baseIndexDir, String.valueOf(projectId));
			if (Files.exists(indexPath)) {
				deleteDirectory(indexPath);
			}
		} catch (IOException e) {
			logger.error("Error deleting index for project {}", projectId, e);
		}
	}

	private void deleteDirectory(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			try (java.util.stream.Stream<Path> entries = Files.list(path)) {
				entries.forEach(entry -> {
					try {
						deleteDirectory(entry);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
		}
		Files.delete(path);
	}

	@PreDestroy
	public void close() {
		searcherManagers.values().forEach(sm -> {
			try {
				sm.close();
			} catch (IOException e) {
				logger.error("Error closing SearcherManager", e);
			}
		});
		writers.values().forEach(writer -> {
			try {
				writer.close();
			} catch (IOException e) {
				logger.error("Error closing IndexWriter", e);
			}
		});
	}
}
