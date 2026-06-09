package com.mcp.dto;

public record CodebaseQuery(
		String filePath,
		String query,
		String sessionId,
		String format,
		String type,
		Integer limit,
		String controllerName,
		String methodName
) {
	public String getFormatOrDefault() {
		return format != null ? format : "full";
	}

	public int getLimitOrDefault() {
		return limit != null ? limit : 10;
	}

	public int getSymbolLimitOrDefault() {
		return limit != null ? limit : 50;
	}

	public int getFileLimitOrDefault() {
		return limit != null ? limit : 100;
	}
}
