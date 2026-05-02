package com.tim.workflow.orchestrator.dto;

import java.time.Instant;

import com.tim.workflow.orchestrator.domain.ExecutionEventType;

public class ExecutionEventResponse {

    private final Long id;
    private final ExecutionEventType eventType;
    private final String payload;
    private final Instant createdAt;

    public ExecutionEventResponse(Long id, ExecutionEventType eventType, String payload, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public ExecutionEventType getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
