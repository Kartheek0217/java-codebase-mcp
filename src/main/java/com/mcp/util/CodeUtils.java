package com.mcp.util;

import java.util.stream.Collectors;

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

	/**
	 * Strips Java import statements from content.
	 * Imports are high-noise for AI tools (they see "import org.springframework..."
	 * 30 times) and contain zero signal about actual logic.
	 */
	public static String stripJavaImports(String content) {
		if (content == null)
			return null;
		return content.lines()
				.filter(line -> !line.stripLeading().startsWith("import "))
				.collect(Collectors.joining("\n"));
	}
}
