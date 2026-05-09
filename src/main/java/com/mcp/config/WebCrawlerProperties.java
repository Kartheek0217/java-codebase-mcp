package com.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mcp.web.crawler")
public class WebCrawlerProperties {

	private int maxPages = 10;
	private int maxConcurrent = 4;
	private int delayMs = 1000;
	private int connectTimeoutMs = 30000;
	private String userAgent = "JavaCodebaseMCP/1.0";
	private int maxLinksPerPage = 100;

	public int getMaxPages() {
		return maxPages;
	}

	public void setMaxPages(int maxPages) {
		this.maxPages = maxPages;
	}

	public int getMaxConcurrent() {
		return maxConcurrent;
	}

	public void setMaxConcurrent(int maxConcurrent) {
		this.maxConcurrent = maxConcurrent;
	}

	public int getDelayMs() {
		return delayMs;
	}

	public void setDelayMs(int delayMs) {
		this.delayMs = delayMs;
	}

	public int getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(int connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public int getMaxLinksPerPage() {
		return maxLinksPerPage;
	}

	public void setMaxLinksPerPage(int maxLinksPerPage) {
		this.maxLinksPerPage = maxLinksPerPage;
	}
}
