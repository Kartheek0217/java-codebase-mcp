package com.mcp.web.search;

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

	public WebSearchService(LuceneIndexService luceneIndexService) {
		this.luceneIndexService = luceneIndexService;
	}

	public List<WebSearchResultDTO> search(WebSearchRequestDTO request) {

		List<ContentSearchResult> rawResults = luceneIndexService.searchContent(request.projectId(), request.query(),
				"web", request.site(), request.limit(), request.offset());

		return rawResults.stream().map(res -> new WebSearchResultDTO(res.filePath(),
				res.title() != null ? res.title() : res.filePath(), res.score(), res.matches().stream()
						.map(ContentSearchResult.ContentMatch::lineContent).collect(Collectors.joining(" ... ")),
				"web")).toList();
	}
}
