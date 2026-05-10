package com.mcp.web.search;

import com.mcp.dto.WebSearchResultDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DuckDuckGoSearchProvider implements WebSearchProvider {

	private static final Logger logger = LoggerFactory.getLogger(DuckDuckGoSearchProvider.class);
	private static final String SEARCH_URL = "https://html.duckduckgo.com/html/";

	@Override
	public List<WebSearchResultDTO> search(String query, String site, int limit, int offset) {
		String fullQuery = query;
		if (site != null && !site.isEmpty()) {
			fullQuery = "site:" + site + " " + query;
		}

		List<WebSearchResultDTO> results = new ArrayList<>();
		try {
			logger.info("Performing DuckDuckGo search for: {}", fullQuery);
			Document doc = Jsoup.connect(SEARCH_URL)
					.data("q", fullQuery)
					.userAgent(
							"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
					.timeout(10000)
					.get();

			// 1. Handle "Zero Click" result (Instant Answer)
			Element zciElement = doc.selectFirst(".zci");
			if (zciElement != null) {
				Element zciTitle = zciElement.selectFirst(".zci__heading a");
				Element zciSnippet = zciElement.selectFirst(".zci__result");
				if (zciTitle != null) {
					String url = zciTitle.attr("href");
					if (url.startsWith("//"))
						url = "https:" + url;
					results.add(new WebSearchResultDTO(url, zciTitle.text(), 1.2,
							zciSnippet != null ? zciSnippet.text() : "", "web_instant"));
				}
			}

			// 2. Handle regular results
			Elements resultElements = doc.select(".result");
			logger.info("Found {} result elements on DuckDuckGo page", resultElements.size());

			int count = 0;
			for (Element element : resultElements) {
				if (count >= limit)
					break;

				Element titleElement = element.selectFirst(".result__title a");
				Element snippetElement = element.selectFirst(".result__snippet");

				if (titleElement != null) {
					String title = titleElement.text();
					String url = titleElement.attr("href");

					// Fix protocol-relative URLs
					if (url.startsWith("//"))
						url = "https:" + url;

					// DuckDuckGo sometimes returns redirects like /l/?kh=-1&uddg=URL
					if (url.contains("uddg=")) {
						String uddg = url.substring(url.indexOf("uddg=") + 5);
						if (uddg.contains("&")) {
							uddg = uddg.substring(0, uddg.indexOf("&"));
						}
						url = java.net.URLDecoder.decode(uddg, java.nio.charset.StandardCharsets.UTF_8);
					}

					String snippet = snippetElement != null ? snippetElement.text() : "";

					results.add(new WebSearchResultDTO(url, title, 1.0, snippet, "web"));
					count++;
				}
			}
		} catch (IOException e) {
			logger.error("Error performing DuckDuckGo search", e);
		}
		return results;
	}
}
