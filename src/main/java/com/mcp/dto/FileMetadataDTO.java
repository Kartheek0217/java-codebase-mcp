package com.mcp.dto;

import java.time.LocalDateTime;

public record FileMetadataDTO(String filePath, Long fileSize, String checksum, LocalDateTime lastScanned) {
}
