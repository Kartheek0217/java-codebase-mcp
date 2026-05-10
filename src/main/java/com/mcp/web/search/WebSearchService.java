package com.mcp.web.search;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mcp.dto.ContentSearchResult;
import com.mcp.dto.WebSearchRequestDTO;
import com.mcp.dto.WebSearchResultDTO;
import com.mcp.service.LuceneIndexService;

@Service
public class WebSearchService {

	private final LuceneIndexService luceneIndexService;
	private final WebSearchProvider webSearchProvider;

	public WebSearchService(LuceneIndexService luceneIndexService, WebSearchProvider webSearchProvider) {
		this.luceneIndexService = luceneIndexService;
		this.webSearchProvider = webSearchProvider;
	}

	public List<WebSearchResultDTO> search(WebSearchRequestDTO request) {
		// 1. Perform external web search (e.g., DuckDuckGo)
		List<WebSearchResultDTO> results = new ArrayList<>(
				webSearchProvider.search(request.query(), request.site(), request.limit(), request.offset()));

		// 2. Also search locally crawled content
		List<ContentSearchResult> rawResults = luceneIndexService.searchContent(request.projectId(), request.query(),
				"web", request.site(), request.limit(), request.offset());

		List<WebSearchResultDTO> localResults = rawResults.stream().map(res -> new WebSearchResultDTO(res.filePath(),
				res.title() != null ? res.title() : res.filePath(), res.score(), res.matches().stream()
						.map(ContentSearchResult.ContentMatch::lineContent).collect(Collectors.joining(" ... ")),
				"crawled")).toList();

		results.addAll(localResults);

		return results;
	}
}
