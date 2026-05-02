package com.tim.workflow.orchestrator.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflows")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @Column(name = "current_version")
    private Integer currentVersion = 1;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Workflow setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Workflow setDescription(String description) {
        this.description = description;
        return this;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public Workflow setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}