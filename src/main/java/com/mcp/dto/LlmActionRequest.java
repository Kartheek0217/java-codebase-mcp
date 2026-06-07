package com.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for unified LLM action")
public record LlmActionRequest(
    @Schema(description = "ID of the associated symbol (for explain-symbol)", example = "101")
    Long symbolId,
    @Schema(description = "Relative file path (for explain-file, code-review, code-refactor, java-doc, junit-test-cases)", example = "src/main/java/App.java")
    String filePath,
    @Schema(description = "Question to ask the codebase (for ask action)", example = "How does tokenization work?")
    String question,
    @Schema(description = "Query string (for web-search)", example = "Spring Boot RestClient timeout")
    String query,
    @Schema(description = "Direct URL to search/analyze (for web-search)", example = "https://spring.io")
    String url,
    @Schema(description = "Git diff content (for code-commit)", example = "--- a/App.java\\n+++ b/App.java")
    String diff
) {}
