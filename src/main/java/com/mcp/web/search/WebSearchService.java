package com.mcp.web.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mcp.dto.WebSearchRequestDTO;
import com.mcp.dto.WebSearchResultDTO;

@Service
public class WebSearchService {

	private final WebSearchProvider webSearchProvider;

	public WebSearchService(WebSearchProvider webSearchProvider) {
		this.webSearchProvider = webSearchProvider;
	}

	public List<WebSearchResultDTO> search(WebSearchRequestDTO request) {
		// Perform external web search (e.g., DuckDuckGo)
		return new ArrayList<>(
				webSearchProvider.search(request.query(), request.site(), request.limit(), request.offset()));
	}
}
