package com.mcp.web.crawler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RobotsTxtParser {
	private static final Logger logger = LoggerFactory.getLogger(RobotsTxtParser.class);
	private final Map<String, String> cache = new ConcurrentHashMap<>();
	private final HttpClient httpClient;

	public RobotsTxtParser() {
		this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(10)).build();
	}

	public boolean isAllowed(String url, String userAgent) {
		try {
			URI uri = new URI(url);
			String host = uri.getHost();
			String protocol = uri.getScheme();
			String robotsUrl = protocol + "://" + host + "/robots.txt";

			String content = cache.computeIfAbsent(host, k -> fetchRobotsTxt(robotsUrl));
			if (content == null || content.isEmpty())
				return true;

			// Simple parsing: look for Disallow: /path
			String path = uri.getPath();
			if (path == null || path.isEmpty())
				path = "/";

			// Basic implementation: check if any Disallow matches the path
			String[] lines = content.split("\n");
			boolean userAgentApplies = false;
			for (String line : lines) {
				line = line.trim();
				if (line.toLowerCase().startsWith("user-agent:")) {
					String ua = line.substring(11).trim();
					userAgentApplies = ua.equals("*") || ua.equalsIgnoreCase(userAgent);
				} else if (userAgentApplies && line.toLowerCase().startsWith("disallow:")) {
					String disallowPath = line.substring(9).trim();
					if (!disallowPath.isEmpty() && path.startsWith(disallowPath)) {
						return false;
					}
				} else if (userAgentApplies && line.toLowerCase().startsWith("allow:")) {
					String allowPath = line.substring(6).trim();
					if (!allowPath.isEmpty() && path.startsWith(allowPath)) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Error parsing robots.txt for {}: {}", url, e.getMessage());
		}
		return true;
	}

	private String fetchRobotsTxt(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5)).build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				return response.body();
			}
		} catch (Exception e) {
			logger.debug("Robots.txt not found or error for {}: {}", url, e.getMessage());
		}
		return "";
	}
}
