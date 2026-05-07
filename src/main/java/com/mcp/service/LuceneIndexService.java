package com.mcp.service;

import com.mcp.dto.ContentSearchResult;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LuceneIndexService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexService.class);
    private static final String BASE_INDEX_DIR = "data/indices";
    private final Map<Long, Object> projectLocks = new ConcurrentHashMap<>();

    public void indexFileContent(Long projectId, String filePath, String content) {
        Object lock = projectLocks.computeIfAbsent(projectId, k -> new Object());
        synchronized (lock) {
            Path indexPath = Paths.get(BASE_INDEX_DIR, String.valueOf(projectId));
            try {
                if (!Files.exists(indexPath)) {
                    Files.createDirectories(indexPath);
                }
                
                try (Directory directory = FSDirectory.open(indexPath);
                     StandardAnalyzer analyzer = new StandardAnalyzer()) {
                    
                    IndexWriterConfig config = new IndexWriterConfig(analyzer);
                    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                    
                    try (IndexWriter writer = new IndexWriter(directory, config)) {
                        Document doc = new Document();
                        doc.add(new StringField("path", filePath, Field.Store.YES));
                        doc.add(new TextField("content", content, Field.Store.YES));
                        
                        writer.updateDocument(new Term("path", filePath), doc);
                        writer.commit();
                    }
                }
            } catch (IOException e) {
                logger.error("Error indexing content for file: {}", filePath, e);
            }
        }
    }

    public void deleteFileContent(Long projectId, String filePath) {
        Object lock = projectLocks.computeIfAbsent(projectId, k -> new Object());
        synchronized (lock) {
            Path indexPath = Paths.get(BASE_INDEX_DIR, String.valueOf(projectId));
            if (!Files.exists(indexPath)) return;

            try (Directory directory = FSDirectory.open(indexPath);
                 StandardAnalyzer analyzer = new StandardAnalyzer()) {
                
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                try (IndexWriter writer = new IndexWriter(directory, config)) {
                    writer.deleteDocuments(new Term("path", filePath));
                    writer.commit();
                }
            } catch (IOException e) {
                logger.error("Error deleting content for file: {}", filePath, e);
            }
        }
    }

    public List<ContentSearchResult> searchContent(Long projectId, String queryStr) {
        List<ContentSearchResult> results = new ArrayList<>();
        Path indexPath = Paths.get(BASE_INDEX_DIR, String.valueOf(projectId));
        if (!Files.exists(indexPath)) return results;

        try (Directory directory = FSDirectory.open(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {
            
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();
            
            QueryParser parser = new QueryParser("content", analyzer);
            Query query = parser.parse(queryStr);
            
            TopDocs topDocs = searcher.search(query, 50);
            StoredFields storedFields = searcher.storedFields();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = storedFields.document(scoreDoc.doc);
                String filePath = doc.get("path");
                
                List<ContentSearchResult.ContentMatch> matches = extractMatches(Paths.get(filePath), queryStr);
                if (!matches.isEmpty()) {
                    results.add(new ContentSearchResult(filePath, matches));
                }
            }
        } catch (Exception e) {
            logger.error("Error searching content in project {}: {}", projectId, queryStr, e);
        }
        return results;
    }

    private List<ContentSearchResult.ContentMatch> extractMatches(Path path, String query) {
        List<ContentSearchResult.ContentMatch> matches = new ArrayList<>();
        try {
            // Simple line-by-line check for the query string
            // For complex Lucene queries, this is a heuristic
            List<String> lines = Files.readAllLines(path);
            String lowerQuery = query.toLowerCase().replace("*", "").replace("?", ""); // Basic cleanup
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.toLowerCase().contains(lowerQuery)) {
                    matches.add(new ContentSearchResult.ContentMatch(i + 1, line.trim()));
                }
                if (matches.size() >= 10) break; // Limit matches per file
            }
        } catch (Exception e) {
            logger.warn("Could not extract matches from file: {}", path);
        }
        return matches;
    }
}
