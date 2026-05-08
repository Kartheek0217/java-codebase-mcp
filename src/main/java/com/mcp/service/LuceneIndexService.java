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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mcp.config.LuceneProperties;
import com.mcp.dto.ContentSearchResult;

import jakarta.annotation.PreDestroy;

@Service
public class LuceneIndexService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexService.class);
    private static final String BASE_INDEX_DIR = "data/indices";
    private static final long SEARCH_TIMEOUT_MS = 2000;

    private final Map<Long, IndexWriter> writers = new ConcurrentHashMap<>();
    private final Map<Long, SearcherManager> searcherManagers = new ConcurrentHashMap<>();
    private final Set<Long> pendingCommits = ConcurrentHashMap.newKeySet();
    private final Set<Long> bulkModeProjects = ConcurrentHashMap.newKeySet();
    private final StandardAnalyzer sharedAnalyzer = new StandardAnalyzer();

    private final LuceneProperties luceneProperties;

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

    private IndexWriter getWriter(Long projectId) throws IOException {
        return writers.computeIfAbsent(projectId, k -> {
            try {
                Path indexPath = Paths.get(BASE_INDEX_DIR, String.valueOf(projectId));
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
                return writer;
            } catch (IOException e) {
                logger.error("Error creating IndexWriter for project {}", projectId, e);
                throw new RuntimeException(e);
            }
        });
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
        try {
            IndexWriter writer = getWriter(projectId);
            Document doc = new Document();
            doc.add(new StringField("path", filePath, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));

            writer.updateDocument(new Term("path", filePath), doc);
            pendingCommits.add(projectId);

            if (!bulkModeProjects.contains(projectId)) {
                SearcherManager sm = searcherManagers.get(projectId);
                if (sm != null) {
                    sm.maybeRefresh();
                }
            }
        } catch (IOException e) {
            logger.error("Error indexing content for file: {}", filePath, e);
        }
    }

    public void deleteFileContent(Long projectId, String filePath) {
        try {
            IndexWriter writer = writers.get(projectId);
            if (writer != null) {
                writer.deleteDocuments(new Term("path", filePath));
                pendingCommits.add(projectId);
                SearcherManager sm = searcherManagers.get(projectId);
                if (sm != null) {
                    sm.maybeRefresh();
                }
            }
        } catch (IOException e) {
            logger.error("Error deleting content for file: {}", filePath, e);
        }
    }

    public List<ContentSearchResult> searchContent(Long projectId, String queryStr) {
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
                    // Fallback: search as a literal phrase if syntax is invalid
                    query = parser.parse(QueryParser.escape(queryStr));
                }

                // Add timeout
                searcher.setTimeout(new IndexSearcherTimeout(SEARCH_TIMEOUT_MS));

                TopDocs topDocs = searcher.search(query, 50);

                if (topDocs.scoreDocs.length > 0) {
                    UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, sharedAnalyzer).build();
                    String[] snippets = highlighter.highlight("content", query, topDocs, 5);

                    StoredFields storedFields = searcher.storedFields();
                    for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                        ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                        Document doc = storedFields.document(scoreDoc.doc);
                        String filePath = doc.get("path");
                        String snippet = (snippets != null && i < snippets.length) ? snippets[i] : "";

                        if (snippet != null && !snippet.isEmpty()) {
                            List<ContentSearchResult.ContentMatch> matches = parseSnippets(snippet);
                            results.add(new ContentSearchResult(filePath, scoreDoc.score, matches));
                        }
                    }
                }
            } finally {
                sm.release(searcher);
            }
        } catch (Exception e) {
            logger.error("Error searching content in project {}: {}", projectId, queryStr, e);
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

    private List<ContentSearchResult.ContentMatch> parseSnippets(String snippet) {
        List<ContentSearchResult.ContentMatch> matches = new ArrayList<>();
        // UnifiedHighlighter by default uses ... as a separator between passages
        String[] passages = snippet.split("(?i)<b>...</b>|\\.\\.\\.");

        for (String passage : passages) {
            String cleanPassage = passage.replaceAll("<b>|</b>", "").trim();
            if (cleanPassage.isEmpty())
                continue;

            // Note: Since we don't have the original line numbers easily from the
            // highlighter
            // without custom PassageFormatter, we'll set it to 0 for now or use a
            // heuristic.
            // For now, let's just provide the snippet content.
            matches.add(new ContentSearchResult.ContentMatch(0, cleanPassage, "unknown", 0, 0));
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

    @Scheduled(fixedDelay = 5000)
    public void scheduledCommit() {
        if (pendingCommits.isEmpty())
            return;

        logger.debug("Running scheduled commit for {} projects", pendingCommits.size());
        java.util.Iterator<Long> iterator = pendingCommits.iterator();
        while (iterator.hasNext()) {
            Long projectId = iterator.next();
            IndexWriter writer = writers.get(projectId);
            if (writer != null) {
                try {
                    writer.commit();
                    SearcherManager sm = searcherManagers.get(projectId);
                    if (sm != null) {
                        sm.maybeRefresh();
                    }
                } catch (IOException e) {
                    logger.error("Error during scheduled commit for project {}", projectId, e);
                }
            }
            iterator.remove();
        }
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
