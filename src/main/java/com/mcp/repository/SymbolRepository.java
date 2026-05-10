package com.mcp.repository;

import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SymbolRepository extends JpaRepository<Symbol, Long> {
	@Modifying
	@Transactional
	void deleteByProjectIdAndFilePath(Long projectId, String filePath);

	@Modifying
	@Transactional
	void deleteByProjectIdAndFilePathIn(Long projectId, List<String> filePaths);

	@Modifying
	@Transactional
	void deleteByProjectId(Long projectId);

	List<Symbol> findByProjectIdAndFilePath(Long projectId, String filePath);

	List<Symbol> findByProjectId(Long projectId);

	List<Symbol> findByProjectIdAndNameContainingIgnoreCase(Long projectId, String name);

	List<Symbol> findByProjectIdAndNameContainingIgnoreCaseAndType(Long projectId, String name,
			SymbolType type);

	long countByProjectId(Long projectId);
}
