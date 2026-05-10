package com.mcp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mcp.entity.CrawledPage;

@Repository
public interface CrawledPageRepository extends JpaRepository<CrawledPage, Long> {
	List<CrawledPage> findByCrawlJobId(Long crawlJobId);

	List<CrawledPage> findByProjectId(Long projectId);

	Optional<CrawledPage> findByUrlAndProjectId(String url, Long projectId);

	boolean existsByUrlAndProjectId(String url, Long projectId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.transaction.annotation.Transactional
	void deleteByProjectId(Long projectId);
}
