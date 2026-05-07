package com.mcp.indexing.sprint0;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class Sprint0ReportWriter {
    Path writeJson(Path filePath, Object report) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, Json.stringify(report));
        return filePath;
    }

    static Sprint0Metadata metadata() {
        return new Sprint0Metadata(
                Instant.now().toString(),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                Runtime.version().toString()
        );
    }

    record Sprint0Metadata(String generatedAt, String osName, String osArch, String javaRuntime) {
    }
}
