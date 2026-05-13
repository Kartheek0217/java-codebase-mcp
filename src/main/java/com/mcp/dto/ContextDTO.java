package com.mcp.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextDTO(String path, String content, String summary, String mode, List<SymbolDTO> symbols, FileMetadataDTO metadata,
		String _correlationId, Boolean alreadyViewed, Boolean hasChanged) {
}
