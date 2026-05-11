package com.mcp.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.WebSearchRequestDTO;
import com.mcp.dto.WebSearchResultDTO;
import com.mcp.web.search.WebSearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/web-search")
@Tag(name = "Web Search", description = "Endpoints for searching crawled web content")
public class WebSearchController {

	private final WebSearchService searchService;
	private final Semaphore rateLimiter = new Semaphore(20);

	public WebSearchController(WebSearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping
	@Operation(summary = "search", description = "Search crawled web content")
	public List<WebSearchResultDTO> search(@RequestParam Long projectId, @RequestParam String q,
			@RequestParam(required = false) String site, @RequestParam(defaultValue = "10") int limit,
			@RequestParam(defaultValue = "0") int offset) {

		try {
			if (!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Server busy, please try again later");
			}
			try {
				return searchService.search(new WebSearchRequestDTO(projectId, q, site, limit, offset));
			} finally {
				rateLimiter.release();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Request interrupted");
		}
	}
}
