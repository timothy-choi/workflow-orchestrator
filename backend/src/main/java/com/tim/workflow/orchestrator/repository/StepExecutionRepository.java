package com.tim.workflow.orchestrator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tim.workflow.orchestrator.domain.StepExecution;

public interface StepExecutionRepository extends JpaRepository<StepExecution, Long> {

    List<StepExecution> findByWorkflowExecutionIdOrderByStepIndexAsc(Long workflowExecutionId);

    Optional<StepExecution> findByWorkflowExecutionIdAndStepName(Long workflowExecutionId, String stepName);
}
