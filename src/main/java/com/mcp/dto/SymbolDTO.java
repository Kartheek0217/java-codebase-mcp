package com.mcp.dto;

import com.mcp.entity.SymbolType;

public record SymbolDTO(String name, SymbolType type, String filePath) {
}
