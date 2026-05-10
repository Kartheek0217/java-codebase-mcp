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

	public static String truncateAndAddLineNumbers(String content, Integer maxLines, String lineRange) {
		if (content == null || content.isEmpty())
			return content;
		String[] lines = content.split("\\r?\\n");

		int start = 0;
		int end = lines.length;

		if (lineRange != null && !lineRange.isEmpty()) {
			String[] range = lineRange.split("-");
			if (range.length == 2) {
				try {
					start = Math.max(0, Integer.parseInt(range[0]) - 1);
					end = Math.min(lines.length, Integer.parseInt(range[1]));
				} catch (NumberFormatException e) {
					// Ignore
				}
			}
		}

		if (maxLines != null && maxLines > 0) {
			end = Math.min(end, start + maxLines);
		}

		if (start >= lines.length)
			return "";
		if (start < 0)
			start = 0;
		if (end > lines.length)
			end = lines.length;
		if (start >= end)
			return "";

		StringBuilder sb = new StringBuilder();
		for (int i = start; i < end; i++) {
			sb.append(i + 1).append(": ").append(lines[i]).append("\n");
		}
		return sb.toString();
	}
}
