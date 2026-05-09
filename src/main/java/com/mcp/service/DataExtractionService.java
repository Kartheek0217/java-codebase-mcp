package com.mcp.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mcp.dto.ExtractionRequestDTO;
import com.mcp.dto.ExtractionResultDTO;

import com.mcp.util.UrlValidator;

@Service
public class DataExtractionService {
	private static final Logger logger = LoggerFactory.getLogger(DataExtractionService.class);

	public ExtractionResultDTO extract(ExtractionRequestDTO request) {
		if (!UrlValidator.isSafeUrl(request.url())) {
			return new ExtractionResultDTO(request.url(), null, "FAILED: URL is not allowed (SSRF protection)");
		}
		try {
			Document doc = Jsoup.connect(request.url()).timeout(30000).get();

			Map<String, Object> data = new HashMap<>();

			for (ExtractionRequestDTO.SelectorDTO selector : request.selectors()) {
				Object result = extractBySelector(doc, selector);
				data.put(selector.name(), result);
			}

			ExtractionResultDTO result = new ExtractionResultDTO(request.url(), data, "SUCCESS");
			if (request.format() != null) {
				return applyFormat(result, request.format());
			}
			return result;
		} catch (IOException e) {
			logger.error("Extraction failed for {}: {}", request.url(), e.getMessage());
			return new ExtractionResultDTO(request.url(), null, "FAILED: " + e.getMessage());
		}
	}

	private ExtractionResultDTO applyFormat(ExtractionResultDTO result, String format) {
		if ("json".equalsIgnoreCase(format)) {
			return result;
		}
		Map<String, Object> data = result.data();
		if (data == null)
			return result;

		StringBuilder formatted = new StringBuilder();
		if ("csv".equalsIgnoreCase(format)) {
			data.forEach((key, value) -> {
				formatted.append(key).append(",").append(value).append("\n");
			});
		} else if ("markdown".equalsIgnoreCase(format)) {
			data.forEach((key, value) -> {
				formatted.append("### ").append(key).append("\n").append(value).append("\n\n");
			});
		} else {
			return result;
		}

		Map<String, Object> formattedData = new HashMap<>();
		formattedData.put("content", formatted.toString());
		return new ExtractionResultDTO(result.url(), formattedData, result.status());
	}

	private Object extractBySelector(Document doc, ExtractionRequestDTO.SelectorDTO selector) {
		if ("table".equalsIgnoreCase(selector.type())) {
			return extractTable(doc, selector.query());
		}

		Elements elements = doc.select(selector.query());
		if (elements.isEmpty())
			return null;

		List<String> results = new ArrayList<>();
		for (Element el : elements) {
			if (selector.attribute() != null && !selector.attribute().isEmpty()) {
				results.add(el.attr(selector.attribute()));
			} else {
				results.add(el.text());
			}
		}

		return results.size() == 1 ? results.get(0) : results;
	}

	private List<List<String>> extractTable(Document doc, String query) {
		Element table = doc.select(query).first();
		if (table == null)
			return null;

		List<List<String>> rows = new ArrayList<>();
		for (Element row : table.select("tr")) {
			List<String> cols = new ArrayList<>();
			for (Element col : row.select("td, th")) {
				cols.add(col.text());
			}
			if (!cols.isEmpty()) {
				rows.add(cols);
			}
		}
		return rows;
	}

	public Map<String, String> extractMetadata(String url) throws IOException {
		if (!UrlValidator.isSafeUrl(url)) {
			throw new IOException("URL is not allowed (SSRF protection)");
		}
		Document doc = Jsoup.connect(url).timeout(10000).get();
		Map<String, String> metadata = new HashMap<>();
		metadata.put("title", doc.title());
		metadata.put("description", doc.select("meta[name=description]").attr("content"));
		metadata.put("keywords", doc.select("meta[name=keywords]").attr("content"));
		metadata.put("og:title", doc.select("meta[property=og:title]").attr("content"));
		metadata.put("og:description", doc.select("meta[property=og:description]").attr("content"));
		return metadata;
	}
}
