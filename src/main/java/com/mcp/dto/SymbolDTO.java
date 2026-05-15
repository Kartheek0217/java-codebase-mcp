package com.mcp.dto;

import com.mcp.entity.SymbolType;

/**
 * Fix E+G: Rich symbol DTO exposing AST-extracted metadata.
 * filePath is relative to the project's rootPath (e.g.
 * "src/main/java/com/mcp/service/Foo.java").
 */
public record SymbolDTO(
		Long id,
		String name,
		SymbolType type,
		String filePath, // Fix G: relative to project rootPath
		Integer lineNumber, // Fix E: exact position in file — AI can jump directly to the symbol
		String signature, // Fix E: full declaration, e.g. "public ContextDTO getFileContext(Long,
							// String)"
		String returnType, // Fix E: e.g. "ResponseEntity<ContextDTO>"
		String modifiers, // Fix E: e.g. "public static final"
		String annotations // Fix F: e.g. "@GetMapping @Cacheable"
) {
}
