package com.mcp.web.search;

import com.mcp.dto.WebSearchResultDTO;
import java.util.List;

public interface WebSearchProvider {
	List<WebSearchResultDTO> search(String query, String site, int limit, int offset);
}
