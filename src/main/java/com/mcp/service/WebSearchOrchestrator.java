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

    private void validateUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null || host.equals("localhost") || host.startsWith("127.") ||
                    host.startsWith("169.254.") || host.startsWith("10.") ||
                    host.startsWith("192.168.") || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
                throw new SecurityException("Invalid or blocked host for web search");
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new SecurityException("Only HTTP/HTTPS protocols are allowed");
            }
        } catch (java.net.URISyntaxException e) {
            throw new SecurityException("Malformed URL", e);
        }
    }

    public String fetchWebSearchContent(String query, String url, String sessionId) {
        String pageText = "";
        if (url != null && !url.isBlank()) {
            validateUrl(url);
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

        if (pageText.length() > 50000) {
            pageText = pageText.substring(0, 50000) + "\n\n...[Content Truncated]...";
        }
        return pageText;
    }
}
