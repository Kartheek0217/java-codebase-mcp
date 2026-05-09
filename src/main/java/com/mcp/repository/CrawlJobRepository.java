package com.mcp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mcp.entity.CrawlJob;
import com.mcp.model.CrawlStatus;

@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {
	List<CrawlJob> findByProjectId(Long projectId);

	List<CrawlJob> findByStatus(CrawlStatus status);
}
