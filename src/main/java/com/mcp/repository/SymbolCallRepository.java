package com.mcp.repository;

import com.mcp.entity.SymbolCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SymbolCallRepository extends JpaRepository<SymbolCall, Long> {
    
    @Modifying
    @Transactional
    void deleteByProjectIdAndCallerFilePath(Long projectId, String callerFilePath);

    @Modifying
    @Transactional
    void deleteByProjectId(Long projectId);

    List<SymbolCall> findByCallerId(Long callerId);

    List<SymbolCall> findByProjectIdAndCalleeName(Long projectId, String calleeName);
}
