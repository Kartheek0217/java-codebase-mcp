package com.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ExtractionRequestDTO(@NotBlank String url, @NotEmpty List<SelectorDTO> selectors, String format) {
	public record SelectorDTO(@NotBlank String name, @NotBlank String query, String type, String attribute) {
	}
}
