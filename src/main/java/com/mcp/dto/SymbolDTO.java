package com.mcp.dto;

import com.mcp.entity.SymbolType;

public record SymbolDTO(Long id, String name, SymbolType type, String filePath) {
}
