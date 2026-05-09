package com.mcp.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CompressedContextDTO(@JsonProperty("p") String path, @JsonProperty("c") String content,
		@JsonProperty("s") List<CompressedSymbolDTO> symbols, @JsonProperty("l") Integer totalLines) {
	public record CompressedSymbolDTO(@JsonProperty("n") String name, @JsonProperty("t") String type) {
	}
}
