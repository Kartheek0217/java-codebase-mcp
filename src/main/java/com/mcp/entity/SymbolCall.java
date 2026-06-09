package com.mcp.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "symbol_calls", indexes = {
    @Index(name = "idx_call_caller", columnList = "caller_id"),
    @Index(name = "idx_call_project_callee", columnList = "project_id, callee_name")
})
public class SymbolCall {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "symbol_call_seq")
    @SequenceGenerator(name = "symbol_call_seq", sequenceName = "symbol_call_seq", allocationSize = 50)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "caller_id", nullable = false)
    private Long callerId;

    @Column(name = "caller_file_path")
    private String callerFilePath;

    @Column(name = "callee_name", nullable = false)
    private String calleeName;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getCallerId() {
        return callerId;
    }

    public void setCallerId(Long callerId) {
        this.callerId = callerId;
    }

    public String getCallerFilePath() {
        return callerFilePath;
    }

    public void setCallerFilePath(String callerFilePath) {
        this.callerFilePath = callerFilePath;
    }

    public String getCalleeName() {
        return calleeName;
    }

    public void setCalleeName(String calleeName) {
        this.calleeName = calleeName;
    }
}
