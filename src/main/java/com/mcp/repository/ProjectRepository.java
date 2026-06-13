package com.mcp.repository;

import com.mcp.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
        SELECT p.id, p.name, p.rootPath,
               COUNT(DISTINCT f.filePath), COUNT(DISTINCT s.id)
        FROM Project p
        LEFT JOIN FileMetadata f ON f.projectId = p.id
        LEFT JOIN Symbol s ON s.projectId = p.id
        GROUP BY p.id, p.name, p.rootPath
        """)
    List<Object[]> findAllProjectSummaries();
}
