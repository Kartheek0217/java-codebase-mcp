package com.mcp.util;

import com.mcp.dto.ContextDTO;
import java.util.stream.Collectors;

public class LlmResponseOptimizer {

    /**
     * Formats a ContextDTO into a dense Markdown representation.
     * This is often more token-efficient for LLMs than raw JSON.
     */
    public static String toMarkdown(ContextDTO dto) {
        if (dto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## File: ").append(dto.path()).append("\n");

        if (dto.summary() != null && !dto.summary().isEmpty()) {
            sb.append("> ").append(dto.summary().replace("\n", "\n> ")).append("\n\n");
        }

        if (dto.mode() != null) {
            sb.append("Mode: ").append(dto.mode()).append("\n");
        }

        if (dto.content() != null) {
            String lang = getLanguage(dto.path());
            sb.append("```").append(lang).append("\n");
            sb.append(dto.content());
            if (!dto.content().endsWith("\n"))
                sb.append("\n");
            sb.append("```\n");
        }

        if (dto.symbols() != null && !dto.symbols().isEmpty()) {
            sb.append("\n### Key Symbols\n");
            String symbols = dto.symbols().stream()
                    .limit(20)
                    .map(s -> "- " + s.name() + " (" + s.type() + ")")
                    .collect(Collectors.joining("\n"));
            sb.append(symbols).append("\n");
        }

        return sb.toString();
    }

    private static String getLanguage(String path) {
        if (path == null)
            return "";
        if (path.endsWith(".java"))
            return "java";
        if (path.endsWith(".py"))
            return "python";
        if (path.endsWith(".js"))
            return "javascript";
        if (path.endsWith(".ts"))
            return "typescript";
        if (path.endsWith(".md"))
            return "markdown";
        return "";
    }
}
