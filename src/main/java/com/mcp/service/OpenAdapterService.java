package com.mcp.service;

import com.mcp.dto.openadapter.CrawlRequest;
import com.mcp.dto.openadapter.ExtractRequest;
import com.mcp.dto.openadapter.ScrapeRequest;
import com.mcp.dto.openadapter.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAdapterService {

    private final RestClient restClient;

    public OpenAdapterService(RestClient openAdapterRestClient) {
        this.restClient = openAdapterRestClient;
    }

    public Object webSearch(SearchRequest request) {
        return restClient.post()
                .uri("/search")
                .body(request)
                .retrieve()
                .body(Object.class);
    }

    public Object newsSearch(SearchRequest request) {
        return restClient.post()
                .uri("/search/news")
                .body(request)
                .retrieve()
                .body(Object.class);
    }

    public Object scrapeUrl(ScrapeRequest request) {
        return restClient.post()
                .uri("/scrape")
                .body(request)
                .retrieve()
                .body(Object.class);
    }

    public String pageToMarkdown(ScrapeRequest request) {
        return restClient.post()
                .uri("/scrape/markdown")
                .body(request)
                .retrieve()
                .body(String.class);
    }

    public Object extractData(ExtractRequest request) {
        return restClient.post()
                .uri("/scrape/extract")
                .body(request)
                .retrieve()
                .body(Object.class);
    }

    public Object crawlSite(CrawlRequest request) {
        return restClient.post()
                .uri("/crawl")
                .body(request)
                .retrieve()
                .body(Object.class);
    }
}
