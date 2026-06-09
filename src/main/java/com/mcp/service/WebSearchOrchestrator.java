package com.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebSearchOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchOrchestrator.class);
    private final HeadlessBrowserService headlessBrowserService;

    public WebSearchOrchestrator(HeadlessBrowserService headlessBrowserService) {
        this.headlessBrowserService = headlessBrowserService;
    }

    public String fetchWebSearchContent(String query, String url, String sessionId) {
        String pageText = "";
        if (url != null && !url.isBlank()) {
            logger.info("R&D Web Search - Navigating directly to URL: {}", url);
            headlessBrowserService.navigate(sessionId, url);
            String title = headlessBrowserService.getTitle(sessionId);
            String bodyText = headlessBrowserService.getContent(sessionId);
            pageText = "URL: " + url + "\nTitle: " + title + "\nContent:\n" + bodyText;
        } else if (query != null && !query.isBlank()) {
            String searchUrl = "https://html.duckduckgo.com/html/?q="
                    + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            logger.info("R&D Web Search - Searching DuckDuckGo for: {}", query);
            headlessBrowserService.navigate(sessionId, searchUrl);
            String bodyText = headlessBrowserService.getContent(sessionId);
            pageText = "Search Query: " + query + "\nResults Page Content:\n" + bodyText;
        } else {
            throw new IllegalArgumentException("Either 'url' or 'query' must be provided for web-search");
        }
        return pageText;
    }
}
