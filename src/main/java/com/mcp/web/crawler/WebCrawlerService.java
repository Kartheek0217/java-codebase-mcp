package com.mcp.web.crawler;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mcp.config.WebCrawlerProperties;
import com.mcp.entity.CrawlJob;
import com.mcp.entity.CrawledPage;
import com.mcp.model.CrawlStatus;
import com.mcp.repository.CrawlJobRepository;
import com.mcp.repository.CrawledPageRepository;
import com.mcp.service.LuceneIndexService;

@Service
public class WebCrawlerService {
	private static final Logger logger = LoggerFactory.getLogger(WebCrawlerService.class);

	private final CrawlJobRepository crawlJobRepository;
	private final CrawledPageRepository crawledPageRepository;
	private final LuceneIndexService luceneIndexService;
	private final WebCrawlerProperties properties;
	private final RobotsTxtParser robotsTxtParser;

	private final Map<Long, ExecutorService> activeCrawls = new ConcurrentHashMap<>();

	public WebCrawlerService(CrawlJobRepository crawlJobRepository, CrawledPageRepository crawledPageRepository,
			LuceneIndexService luceneIndexService, WebCrawlerProperties properties, RobotsTxtParser robotsTxtParser) {
		this.crawlJobRepository = crawlJobRepository;
		this.crawledPageRepository = crawledPageRepository;
		this.luceneIndexService = luceneIndexService;
		this.properties = properties;
		this.robotsTxtParser = robotsTxtParser;
	}

	public void startCrawl(Long jobId) {
		CrawlJob job = crawlJobRepository.findById(jobId).orElseThrow();
		if (job.getStatus() != CrawlStatus.PENDING && job.getStatus() != CrawlStatus.PAUSED) {
			return;
		}

		job.setStatus(CrawlStatus.RUNNING);
		job.setStartedAt(LocalDateTime.now());
		crawlJobRepository.save(job);

		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		activeCrawls.put(jobId, executor);

		Set<String> visited = Collections.synchronizedSet(new HashSet<>());
		AtomicInteger count = new AtomicInteger(0);
		Semaphore semaphore = new Semaphore(properties.getMaxConcurrent());

		executor.submit(() -> {
			try {
				// Try sitemap first
				trySitemap(job, visited, count, semaphore);
				runCrawl(job, visited, count, semaphore);
				if (job.getStatus() == CrawlStatus.RUNNING) {
					job.setStatus(CrawlStatus.COMPLETED);
				}
			} catch (Exception e) {
				logger.error("Crawl failed for job {}", jobId, e);
				job.setStatus(CrawlStatus.FAILED);
			} finally {
				job.setCompletedAt(LocalDateTime.now());
				crawlJobRepository.save(job);
				activeCrawls.remove(jobId);
				executor.shutdown();
			}
		});
	}

	private void runCrawl(CrawlJob job, Set<String> visited, AtomicInteger count, Semaphore semaphore) {
		crawlUrl(job, job.getStartUrl(), 0, visited, count, semaphore);
	}

	private void crawlUrl(CrawlJob job, String url, int depth, Set<String> visited, AtomicInteger count,
			Semaphore semaphore) {
		if (depth > job.getMaxDepth() || count.get() >= job.getMaxPages() || visited.contains(url)) {
			return;
		}

		if (job.getStatus() != CrawlStatus.RUNNING) {
			return;
		}

		// Periodic check in database for cancellation
		if (count.get() % 5 == 0) {
			CrawlJob currentJob = crawlJobRepository.findById(job.getId()).orElse(null);
			if (currentJob != null && currentJob.getStatus() == CrawlStatus.CANCELLED) {
				job.setStatus(CrawlStatus.CANCELLED);
				return;
			}
		}

		// Deduplication across jobs for the same project
		if (crawledPageRepository.existsByUrlAndProjectId(url, job.getProjectId())) {
			return;
		}

		if (job.isRespectRobots() && !robotsTxtParser.isAllowed(url, properties.getUserAgent())) {
			logger.info("URL disallowed by robots.txt: {}", url);
			return;
		}

		visited.add(url);

		try {
			if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
				logger.warn("Timeout waiting for semaphore for URL: {}", url);
				return;
			}
			try {
				// Politeness delay
				Thread.sleep(job.getDelayMs());

				Connection.Response response = null;
				int retries = 3;
				while (retries > 0) {
					try {
						response = Jsoup.connect(url).userAgent(properties.getUserAgent())
								.timeout(properties.getConnectTimeoutMs()).followRedirects(true).execute();
						break;
					} catch (IOException e) {
						retries--;
						if (retries == 0)
							throw e;
						Thread.sleep(1000 * (3 - retries));
					}
				}

				if (response == null || response.statusCode() != 200) {
					return;
				}

				// Check for binary content
				String contentType = response.contentType();
				if (contentType != null && !contentType.contains("text/html")
						&& !contentType.contains("application/xhtml")) {
					logger.info("Skipping non-HTML content: {} ({})", url, contentType);
					return;
				}

				Document doc = response.parse();
				String title = doc.title();
				String content = doc.body().text();
				String outerHtml = doc.outerHtml();

				// Calculate checksum
				String checksum = calculateChecksum(outerHtml);

				CrawledPage page = new CrawledPage();
				page.setCrawlJobId(job.getId());
				page.setProjectId(job.getProjectId());
				page.setUrl(url);
				page.setTitle(title);
				page.setMimeType(contentType);
				page.setContentLength(outerHtml.getBytes().length);
				page.setStatusCode(response.statusCode());
				page.setRawContent(outerHtml);
				page.setChecksum(checksum);
				crawledPageRepository.save(page);

				// Index in Lucene
				luceneIndexService.indexFileContent(job.getProjectId(), url, title, content, "web");

				int currentCount = count.incrementAndGet();
				job.setPagesCrawled(currentCount);
				crawlJobRepository.save(job);

				if (depth < job.getMaxDepth()) {
					Elements links = doc.select("a[href]");
					ExecutorService executor = activeCrawls.get(job.getId());
					if (executor != null) {
						links.stream().limit(properties.getMaxLinksPerPage()).forEach(link -> {
							String absUrl = link.attr("abs:href");
							if (isValidUrl(absUrl, job)) {
								executor.submit(() -> crawlUrl(job, absUrl, depth + 1, visited, count, semaphore));
							}
						});
					}
				}
			} finally {
				semaphore.release();
			}

		} catch (IOException | InterruptedException e) {
			logger.error("Error crawling {}: {}", url, e.getMessage());
		}
	}

	private String calculateChecksum(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (Exception e) {
			return null;
		}
	}

	private boolean isValidUrl(String url, CrawlJob job) {
		if (url == null || url.isEmpty())
			return false;
		if (!url.startsWith("http"))
			return false;

		// Simple domain matching to stay on the same site for now, or just follow
		// include/exclude patterns
		try {
			URI startUri = new URI(job.getStartUrl());
			URI currentUri = new URI(url);
			if (!startUri.getHost().equals(currentUri.getHost())) {
				// For now, only crawl same host unless patterns are provided
				if (job.getIncludePattern() == null)
					return false;
			}
		} catch (Exception e) {
			return false;
		}

		if (job.getExcludePattern() != null && url.matches(job.getExcludePattern()))
			return false;
		if (job.getIncludePattern() != null && !url.matches(job.getIncludePattern()))
			return false;

		return true;
	}

	public void stopCrawl(Long jobId) {
		CrawlJob job = crawlJobRepository.findById(jobId).orElseThrow();
		job.setStatus(CrawlStatus.CANCELLED);
		crawlJobRepository.save(job);

		ExecutorService executor = activeCrawls.get(jobId);
		if (executor != null) {
			executor.shutdownNow();
			activeCrawls.remove(jobId);
		}
	}

	private void trySitemap(CrawlJob job, Set<String> visited, AtomicInteger count, Semaphore semaphore) {
		try {
			URI startUri = new URI(job.getStartUrl());
			String sitemapUrl = startUri.getScheme() + "://" + startUri.getHost()
					+ (startUri.getPort() != -1 ? ":" + startUri.getPort() : "") + "/sitemap.xml";

			if (visited.contains(sitemapUrl))
				return;

			logger.info("Attempting to fetch sitemap: {}", sitemapUrl);
			Connection.Response response = Jsoup.connect(sitemapUrl).userAgent(properties.getUserAgent()).timeout(5000)
					.execute();

			if (response.statusCode() == 200) {
				Document doc = response.parse();
				Elements locs = doc.select("loc");
				ExecutorService executor = activeCrawls.get(job.getId());
				if (executor != null) {
					locs.stream().limit(properties.getMaxPages()).forEach(loc -> {
						String url = loc.text();
						if (isValidUrl(url, job)) {
							executor.submit(() -> crawlUrl(job, url, 0, visited, count, semaphore));
						}
					});
				}
			}
		} catch (Exception e) {
			logger.debug("Sitemap not found or invalid: {}", e.getMessage());
		}
	}
}
