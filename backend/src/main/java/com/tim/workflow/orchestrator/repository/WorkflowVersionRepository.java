package com.tim.workflow.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tim.workflow.orchestrator.domain.WorkflowVersion;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
}
