package com.mcp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata")
@IdClass(FileMetadataId.class)
public class FileMetadata {

	@Id
	@Column(name = "project_id")
	private Long projectId;

	@Id
	@Column(name = "file_path")
	private String filePath;

	private String checksum;
	private Long fileSize;
	private LocalDateTime lastScanned;
	@Column(columnDefinition = "TEXT")
	private String dependencies;

	// Getters and Setters
	public String getDependencies() {
		return dependencies;
	}

	public void setDependencies(String dependencies) {
		this.dependencies = dependencies;
	}
	public Long getProjectId() {
		return projectId;
	}

	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getChecksum() {
		return checksum;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

	public LocalDateTime getLastScanned() {
		return lastScanned;
	}

	public void setLastScanned(LocalDateTime lastScanned) {
		this.lastScanned = lastScanned;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}
}
