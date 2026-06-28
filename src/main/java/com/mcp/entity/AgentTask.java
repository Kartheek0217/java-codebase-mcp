package com.mcp.entity;

import com.mcp.model.AgentTaskStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
@Entity
@Table(name = "agent_tasks")
@EntityListeners(AuditingEntityListener.class)
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String taskType;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String taskDescription;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String taskResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AgentTaskStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "mainTaskId", referencedColumnName = "id", insertable = false, updatable = false)
    private List<AgentSubTask> subTasks;

    public AgentTask() {
    }

    public AgentTask(Long projectId, String taskType, String taskDescription) {
        this.projectId = projectId;
        this.taskType = taskType;
        this.taskDescription = taskDescription;
        this.status = AgentTaskStatus.PENDING;
    }

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

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getTaskResponse() {
        return taskResponse;
    }

    public void setTaskResponse(String taskResponse) {
        this.taskResponse = taskResponse;
    }

    public AgentTaskStatus getStatus() {
        return status;
    }

    public void setStatus(AgentTaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<AgentSubTask> getSubTasks() {
        return subTasks;
    }

    public void setSubTasks(List<AgentSubTask> subTasks) {
        this.subTasks = subTasks;
    }
}
