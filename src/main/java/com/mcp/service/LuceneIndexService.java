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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    private IndexWriter getWriter(Long projectId) throws IOException {
        return writers.computeIfAbsent(projectId, k -> {
            try {
                Path indexPath = Paths.get(BASE_INDEX_DIR, String.valueOf(projectId));
                if (!Files.exists(indexPath)) {
                    Files.createDirectories(indexPath);
                }
                Directory directory = FSDirectory.open(indexPath);
                StandardAnalyzer analyzer = new StandardAnalyzer();
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
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

            SearcherManager sm = searcherManagers.get(projectId);
            if (sm != null) {
                sm.maybeRefresh();
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
                StandardAnalyzer analyzer = new StandardAnalyzer();
                QueryParser parser = new QueryParser("content", analyzer);
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
                StoredFields storedFields = searcher.storedFields();

                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = storedFields.document(scoreDoc.doc);
                    String filePath = doc.get("path");
                    String content = doc.get("content");

                    if (content == null)
                        continue;

                    List<ContentSearchResult.ContentMatch> matches = extractMatchesFromContent(content, queryStr);
                    if (!matches.isEmpty()) {
                        results.add(new ContentSearchResult(filePath, scoreDoc.score, matches));
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

    private List<ContentSearchResult.ContentMatch> extractMatchesFromContent(String content, String query) {
        List<ContentSearchResult.ContentMatch> matches = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            String[] allLines = content.split("\\r?\\n");
            int lineNum = 0;
            String lowerQuery = query.toLowerCase().replace("*", "").replace("?", "").replace("\"", "").trim();

            if (lowerQuery.isEmpty())
                return matches;

            List<String> lineList = List.of(allLines);
            for (String line : allLines) {
                if (System.currentTimeMillis() - startTime > 500)
                    break;

                lineNum++;
                if (line.toLowerCase().contains(lowerQuery)) {
                    String functionName = findContainingFunction(lineList, lineNum);
                    int startLine = Math.max(1, lineNum - 3);
                    int endLine = Math.min(allLines.length, lineNum + 3);

                    matches.add(new ContentSearchResult.ContentMatch(lineNum, line.trim(), functionName, startLine,
                            endLine));
                    if (matches.size() >= 10)
                        break;
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract matches from content: {}", e.getMessage());
        }
        return matches;
    }

    private String findContainingFunction(List<String> lines, int currentLine) {
        // Heuristic: scan upwards for method or class definitions
        for (int i = currentLine - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("//") || line.startsWith("/*")
                    || line.startsWith("*"))
                continue;

            // Method signature heuristic: contains ( and ) and { (or { on next line)
            if (line.contains("(") && line.contains(")")) {
                int parenIdx = line.indexOf("(");
                String beforeParen = line.substring(0, parenIdx).trim();
                String[] parts = beforeParen.split("\\s+");
                if (parts.length > 0) {
                    String name = parts[parts.length - 1];
                    // Basic check to avoid keywords
                    if (!name.equals("if") && !name.equals("for") && !name.equals("while") && !name.equals("switch")
                            && !name.equals("catch")) {
                        return name;
                    }
                }
            }
            // Class signature heuristic
            if (line.contains("class ") || line.contains("interface ") || line.contains("enum ")
                    || line.contains("record ")) {
                String type = "class ";
                if (line.contains("interface "))
                    type = "interface ";
                else if (line.contains("enum "))
                    type = "enum ";
                else if (line.contains("record "))
                    type = "record ";

                int classIdx = line.indexOf(type);
                String afterClass = line.substring(classIdx + type.length()).trim();
                String[] parts = afterClass.split("\\s+");
                if (parts.length > 0) {
                    return parts[0];
                }
            }
        }
        return "unknown";
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
