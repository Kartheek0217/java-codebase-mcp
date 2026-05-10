package com.mcp.entity;

import com.mcp.util.CompressionUtils;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "crawled_page")
public class CrawledPage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "crawl_job_id")
	private Long crawlJobId;

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "url", length = 2048, nullable = false)
	private String url;

	@Column(name = "title", length = 500)
	private String title;

	@Column(name = "mime_type", length = 100)
	private String mimeType;

	@Column(name = "content_length")
	private long contentLength;

	@Column(name = "status_code")
	private int statusCode;

	@Column(name = "checksum", length = 64)
	private String checksum;

	@Lob
	@Column(name = "raw_content")
	private byte[] compressedContent;

	@Column(name = "crawled_at")
	private LocalDateTime crawledAt = LocalDateTime.now();

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCrawlJobId() {
		return crawlJobId;
	}

	public void setCrawlJobId(Long crawlJobId) {
		this.crawlJobId = crawlJobId;
	}

	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public long getContentLength() {
		return contentLength;
	}

	public void setContentLength(long contentLength) {
		this.contentLength = contentLength;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getChecksum() {
		return checksum;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

	public String getRawContent() {
		try {
			return CompressionUtils.decompress(compressedContent);
		} catch (java.io.IOException e) {
			return null;
		}
	}

	public void setRawContent(String rawContent) {
		try {
			this.compressedContent = CompressionUtils.compress(rawContent);
		} catch (java.io.IOException e) {
			this.compressedContent = null;
		}
	}

	public LocalDateTime getCrawledAt() {
		return crawledAt;
	}

	public void setCrawledAt(LocalDateTime crawledAt) {
		this.crawledAt = crawledAt;
	}
}
