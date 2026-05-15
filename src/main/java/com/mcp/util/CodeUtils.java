package com.mcp.util;

public class CodeUtils {

	private CodeUtils() {

	}

	public static String addLineNumbers(String content) {
		if (content == null || content.isEmpty())
			return content;
		String[] lines = content.split("\\r?\\n");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			sb.append(i + 1).append(": ").append(lines[i]).append("\n");
		}
		return sb.toString();
	}
}
