package com.mcp.model;

import java.nio.file.Path;

public record FileEvent(Type type, Path path) {
    public enum Type {
        CREATED,
        MODIFIED,
        DELETED
    }
}
