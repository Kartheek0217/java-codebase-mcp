package com.mcp.repository;

import com.mcp.entity.FileMetadata;
import com.mcp.entity.FileMetadataId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, FileMetadataId> {
	List<FileMetadata> findByProjectId(Long projectId);

	List<FileMetadata> findByProjectIdAndFilePathContainingIgnoreCase(Long projectId, String query);

	@Modifying
	@Transactional
	void deleteByProjectId(Long projectId);

	@Modifying
	@Transactional
	void deleteByProjectIdAndFilePathIn(Long projectId, List<String> filePaths);

	long countByProjectId(Long projectId);
}
