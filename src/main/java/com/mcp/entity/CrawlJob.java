package com.mcp.entity;

import java.time.LocalDateTime;

import com.mcp.model.CrawlStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "crawl_job")
public class CrawlJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "start_url", length = 2048, nullable = false)
	private String startUrl;

	@Column(name = "max_depth")
	private int maxDepth = 3;

	@Column(name = "max_pages")
	private int maxPages = 10;

	@Column(name = "delay_ms")
	private int delayMs = 1000;

	@Column(name = "respect_robots")
	private boolean respectRobots = true;

	@Enumerated(EnumType.STRING)
	private CrawlStatus status = CrawlStatus.PENDING;

	@Column(name = "pages_crawled")
	private int pagesCrawled = 0;

	@Column(name = "include_pattern", length = 2048)
	private String includePattern;

	@Column(name = "exclude_pattern", length = 2048)
	private String excludePattern;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public String getStartUrl() {
		return startUrl;
	}

	public void setStartUrl(String startUrl) {
		this.startUrl = startUrl;
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	public void setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public int getMaxPages() {
		return maxPages;
	}

	public void setMaxPages(int maxPages) {
		this.maxPages = maxPages;
	}

	public int getDelayMs() {
		return delayMs;
	}

	public void setDelayMs(int delayMs) {
		this.delayMs = delayMs;
	}

	public boolean isRespectRobots() {
		return respectRobots;
	}

	public void setRespectRobots(boolean respectRobots) {
		this.respectRobots = respectRobots;
	}

	public CrawlStatus getStatus() {
		return status;
	}

	public void setStatus(CrawlStatus status) {
		this.status = status;
	}

	public int getPagesCrawled() {
		return pagesCrawled;
	}

	public void setPagesCrawled(int pagesCrawled) {
		this.pagesCrawled = pagesCrawled;
	}

	public String getIncludePattern() {
		return includePattern;
	}

	public void setIncludePattern(String includePattern) {
		this.includePattern = includePattern;
	}

	public String getExcludePattern() {
		return excludePattern;
	}

	public void setExcludePattern(String excludePattern) {
		this.excludePattern = excludePattern;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}
}
