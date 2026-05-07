package com.mcp.entity;

import java.io.Serializable;
import java.util.Objects;

public class FileMetadataId implements Serializable {
    private Long projectId;
    private String filePath;

    public FileMetadataId() {}
    public FileMetadataId(Long projectId, String filePath) {
        this.projectId = projectId;
        this.filePath = filePath;
    }

    // Getters, Setters, Equals, HashCode
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadataId that = (FileMetadataId) o;
        return Objects.equals(projectId, that.projectId) && Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, filePath);
    }
}
