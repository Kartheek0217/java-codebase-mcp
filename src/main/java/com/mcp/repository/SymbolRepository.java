package com.mcp.repository;

import com.mcp.entity.Symbol;
import com.mcp.entity.SymbolType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	// Fix L: Pageable overloads push LIMIT to DB rather than loading all rows into JVM
	List<Symbol> findByProjectIdAndNameContainingIgnoreCase(Long projectId, String name, Pageable pageable);

	List<Symbol> findByProjectIdAndNameContainingIgnoreCaseAndType(Long projectId, String name,
			SymbolType type, Pageable pageable);

	// Fix K: aggregate in DB instead of loading all symbols into memory for topology
	@Query("SELECT s.name, COUNT(s) FROM Symbol s WHERE s.projectId = :projectId GROUP BY s.name ORDER BY COUNT(s) DESC")
	List<Object[]> findTopSymbolNames(@Param("projectId") Long projectId, Pageable pageable);

	long countByProjectId(Long projectId);
}
