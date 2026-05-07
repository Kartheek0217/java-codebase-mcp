package com.mcp.dto;

import java.util.List;

public record ContentSearchResult(String filePath, List<ContentMatch> matches) {
    public record ContentMatch(int lineNumber, String lineContent) {}
}
