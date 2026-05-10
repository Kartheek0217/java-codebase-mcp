package com.mcp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mcp.dto.CrawlJobRequestDTO;
import com.mcp.dto.CrawlJobResponseDTO;
import com.mcp.entity.CrawlJob;
import com.mcp.entity.CrawledPage;
import com.mcp.repository.CrawlJobRepository;
import com.mcp.repository.CrawledPageRepository;
import com.mcp.web.crawler.WebCrawlerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/web/crawl")
@Tag(name = "Web Crawler", description = "Endpoints for managing web crawling jobs")
public class WebCrawlController {

	private final WebCrawlerService crawlerService;
	private final CrawlJobRepository jobRepository;
	private final CrawledPageRepository pageRepository;

	public WebCrawlController(WebCrawlerService crawlerService, CrawlJobRepository jobRepository,
			CrawledPageRepository pageRepository) {
		this.crawlerService = crawlerService;
		this.jobRepository = jobRepository;
		this.pageRepository = pageRepository;
	}

	@PostMapping
	@Operation(summary = "Create and start a new crawl job")
	public ResponseEntity<CrawlJobResponseDTO> createCrawl(@Valid @RequestBody CrawlJobRequestDTO request) {
		CrawlJob job = new CrawlJob();
		job.setProjectId(request.projectId());
		job.setStartUrl(request.startUrl());
		job.setMaxDepth(request.maxDepth() != null ? request.maxDepth() : 2);
		job.setMaxPages(request.maxPages() != null ? request.maxPages() : 100);
		job.setDelayMs(request.delayMs() != null ? request.delayMs() : 0);
		job.setRespectRobots(request.respectRobotsTxt() != null ? request.respectRobotsTxt() : true);
		if (request.includePatterns() != null) {
			job.setIncludePattern(String.join("|", request.includePatterns()));
		}
		if (request.excludePatterns() != null) {
			job.setExcludePattern(String.join("|", request.excludePatterns()));
		}

		job = jobRepository.save(job);
		crawlerService.startCrawl(job.getId());

		return ResponseEntity.ok(toResponseDTO(job));
	}

	@GetMapping
	@Operation(summary = "List all crawl jobs")
	public List<CrawlJobResponseDTO> listJobs() {
		return jobRepository.findAll().stream().map(this::toResponseDTO).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get crawl job details")
	public ResponseEntity<CrawlJobResponseDTO> getJob(@PathVariable Long id) {
		return jobRepository.findById(id).map(job -> ResponseEntity.ok(toResponseDTO(job)))
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/pages")
	@Operation(summary = "Get crawled pages for a job")
	public List<CrawledPage> getPages(@PathVariable Long id) {
		return pageRepository.findByCrawlJobId(id);
	}

	@PostMapping("/{id}/stop")
	@Operation(summary = "Cancel a running crawl job")
	public ResponseEntity<Void> stopCrawl(@PathVariable Long id) {
		crawlerService.stopCrawl(id);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a crawl job and its results")
	public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
		jobRepository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	private CrawlJobResponseDTO toResponseDTO(CrawlJob job) {
		return new CrawlJobResponseDTO(job.getId(), job.getProjectId(), job.getStartUrl(), job.getStatus(),
				job.getPagesCrawled(), job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt());
	}
}
