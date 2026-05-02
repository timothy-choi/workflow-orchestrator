package com.tim.workflow.orchestrator.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "execution_events")
public class ExecutionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_execution_id", nullable = false)
    private Long workflowExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private ExecutionEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getWorkflowExecutionId() {
        return workflowExecutionId;
    }

    public ExecutionEvent setWorkflowExecutionId(Long workflowExecutionId) {
        this.workflowExecutionId = workflowExecutionId;
        return this;
    }

    public ExecutionEventType getEventType() {
        return eventType;
    }

    public ExecutionEvent setEventType(ExecutionEventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public String getPayload() {
        return payload;
    }

    public ExecutionEvent setPayload(String payload) {
        this.payload = payload;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ExecutionEvent setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
