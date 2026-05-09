package com.mcp.dto;

public record FileMetadataDTO(String filePath, Long fileSize, String checksum) {
}
