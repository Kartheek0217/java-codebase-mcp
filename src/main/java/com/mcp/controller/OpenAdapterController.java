package com.mcp.controller;

import com.mcp.dto.openadapter.CrawlRequest;
import com.mcp.dto.openadapter.ExtractRequest;
import com.mcp.dto.openadapter.ScrapeRequest;
import com.mcp.dto.openadapter.SearchRequest;
import com.mcp.service.OpenAdapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
@Tag(name = "OpenAdapter Tools", description = "Endpoints for web search, scraping, and crawling via OpenAdapter")
public class OpenAdapterController {

    private final OpenAdapterService openAdapterService;

    public OpenAdapterController(OpenAdapterService openAdapterService) {
        this.openAdapterService = openAdapterService;
    }

    @PostMapping("/search")
    @Operation(summary = "Web Search", description = "Perform a web search using OpenAdapter")
    public Object webSearch(@RequestBody SearchRequest request) {
        return openAdapterService.webSearch(request);
    }

    @PostMapping("/search/news")
    @Operation(summary = "News Search", description = "Perform a news search using OpenAdapter")
    public Object newsSearch(@RequestBody SearchRequest request) {
        return openAdapterService.newsSearch(request);
    }

    @PostMapping("/scrape")
    @Operation(summary = "Scrape URL", description = "Scrape content from a URL")
    public Object scrapeUrl(@RequestBody ScrapeRequest request) {
        return openAdapterService.scrapeUrl(request);
    }

    @PostMapping("/scrape/markdown")
    @Operation(summary = "Page to Markdown", description = "Convert a web page to markdown")
    public String pageToMarkdown(@RequestBody ScrapeRequest request) {
        return openAdapterService.pageToMarkdown(request);
    }

    @PostMapping("/scrape/extract")
    @Operation(summary = "Extract Data", description = "Extract specific data from a URL using selectors")
    public Object extractData(@RequestBody ExtractRequest request) {
        return openAdapterService.extractData(request);
    }

    @PostMapping("/crawl")
    @Operation(summary = "Crawl Site", description = "Crawl a website starting from a URL")
    public Object crawlSite(@RequestBody CrawlRequest request) {
        return openAdapterService.crawlSite(request);
    }
}
