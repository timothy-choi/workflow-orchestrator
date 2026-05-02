package com.tim.workflow.orchestrator.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tim.workflow.orchestrator.domain.WorkflowVersion;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
    Optional<WorkflowVersion> findByWorkflowIdAndVersionNumber(Long workflowId, Integer versionNumber);
}
