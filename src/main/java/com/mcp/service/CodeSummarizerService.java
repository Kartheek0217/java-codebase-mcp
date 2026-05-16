package com.mcp.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CodeSummarizerService {

    private static final Logger logger = LoggerFactory.getLogger(CodeSummarizerService.class);
    private final JavaParser javaParser;

    public CodeSummarizerService() {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        this.javaParser = new JavaParser(config);
    }

    /**
     * Extracts the structural skeleton of a Java file.
     * Keeps declarations but replaces method bodies with empty blocks '{ }'.
     */
    public String extractStructure(String content) {
        if (content == null)
            return null;
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                return content; // Fallback
            }
            CompilationUnit cu = parseResult.getResult().get();

            cu.findAll(MethodDeclaration.class).forEach(m -> {
                if (m.getBody().isPresent()) {
                    m.setBody(new BlockStmt());
                }
            });
            cu.findAll(ConstructorDeclaration.class).forEach(c -> c.setBody(new BlockStmt()));

            return cu.toString();
        } catch (Exception e) {
            logger.warn("Failed to parse AST for structure extraction", e);
            return content; // Fallback
        }
    }

    /**
     * Creates a high-level summary of the file by extracting key symbols and
     * Javadocs.
     */
    public String createIntelligentSummary(String content) {
        if (content == null)
            return null;

        StringBuilder summary = new StringBuilder();
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                return "";
            }
            CompilationUnit cu = parseResult.getResult().get();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                summary.append("Component: ").append(c.getNameAsString()).append("\n");
                c.getJavadoc().ifPresent(javadoc -> {
                    String doc = javadoc.toText().replaceAll("\\s+", " ").trim();
                    summary.append("Description: ").append(doc).append("\n");
                });
            });

        } catch (Exception e) {
            logger.warn("Failed to parse AST for summary extraction", e);
        }

        return summary.toString();
    }
}
