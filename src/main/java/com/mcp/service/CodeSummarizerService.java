package com.mcp.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CodeSummarizerService {

    /**
     * Extracts the structural skeleton of a Java file.
     * Keeps declarations but replaces method bodies with ' { ... } '.
     */
    public String extractStructure(String content) {
        if (content == null)
            return null;

        // Simple regex-based approach for Java
        // This targets method bodies: { followed by content and closing }
        // We look for patterns like 'methodName(...) { ... }'

        StringBuilder skeleton = new StringBuilder();
        String[] lines = content.split("\n");
        int braceDepth = 0;
        boolean inMethod = false;

        for (String line : lines) {

            // Basic tracking of brace depth to identify method bodies
            for (char c : line.toCharArray()) {
                if (c == '{')
                    braceDepth++;
                if (c == '}')
                    braceDepth--;
            }

            if (braceDepth > 1) {
                if (!inMethod) {
                    // Just started a method body
                    if (line.contains("{")) {
                        skeleton.append(line.substring(0, line.indexOf("{") + 1)).append(" ... }\n");
                    }
                    inMethod = true;
                }
                continue; // Skip the rest of the body
            } else {
                if (inMethod) {
                    inMethod = false;
                    continue; // Skip the closing brace line since we added it
                }
                skeleton.append(line).append("\n");
            }
        }

        return skeleton.toString();
    }

    /**
     * Creates a high-level summary of the file by extracting key symbols and
     * Javadocs.
     */
    public String createIntelligentSummary(String content) {
        if (content == null)
            return null;

        // Extract class name and main methods
        StringBuilder summary = new StringBuilder();

        Pattern classPattern = Pattern.compile(
                "(?:public|protected|private|static|final|abstract|interface|class|enum)\\s+([A-Z][a-zA-Z0-9_]*)");
        Matcher classMatcher = classPattern.matcher(content);
        if (classMatcher.find()) {
            summary.append("Component: ").append(classMatcher.group(1)).append("\n");
        }

        // Extract Javadoc-style comments
        Pattern javadocPattern = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);
        Matcher javadocMatcher = javadocPattern.matcher(content);
        if (javadocMatcher.find()) {
            String doc = javadocMatcher.group(1).replaceAll("\\*|\\s+", " ").trim();
            summary.append("Description: ").append(doc).append("\n");
        }

        return summary.toString();
    }
}
