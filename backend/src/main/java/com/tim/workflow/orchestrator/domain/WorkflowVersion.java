package com.tim.workflow.orchestrator.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow_versions")
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private Long workflowId;

    @Column(name = "version_number")
    private Integer versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", columnDefinition = "jsonb", nullable = false)
    private String definitionJson;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public WorkflowVersion setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public WorkflowVersion setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
        return this;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public WorkflowVersion setDefinitionJson(String definitionJson) {
        this.definitionJson = definitionJson;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}