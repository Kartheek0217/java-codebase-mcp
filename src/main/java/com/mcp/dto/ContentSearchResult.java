package com.mcp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Represents a search result within a file's content.")
public record ContentSearchResult(
        @Schema(description = "Relative or absolute path to the file") String filePath,
        @Schema(description = "Relevance score of the result") double score,
        @Schema(description = "List of specific line matches found in the file") List<ContentMatch> matches) {
    @Schema(description = "A specific line matching the search query.")
    public record ContentMatch(
            @Schema(description = "The 1-indexed line number where the match was found") int lineNumber,
            @Schema(description = "The actual text content of the matching line") String lineContent,
            @Schema(description = "The name of the function containing this match") String functionName,
            @Schema(description = "The start line of the relevant code block") int lineStart,
            @Schema(description = "The end line of the relevant code block") int lineEnd) {
    }
}
