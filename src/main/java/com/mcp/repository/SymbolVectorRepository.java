package com.mcp.repository;

import com.mcp.entity.SymbolVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymbolVectorRepository extends JpaRepository<SymbolVector, Long> {

    @Query(value = "SELECT * FROM symbol_vectors v ORDER BY cosine_similarity(v.vector, :vector) DESC LIMIT :k", nativeQuery = true)
    List<SymbolVector> findNearestNeighbors(@Param("vector") byte[] vector, @Param("k") int k);

    @Query(value = "SELECT * FROM symbol_vectors v ORDER BY l2_similarity(v.vector, :vector) ASC LIMIT :k", nativeQuery = true)
    List<SymbolVector> findNearestNeighborsL2(@Param("vector") byte[] vector, @Param("k") int k);
}
