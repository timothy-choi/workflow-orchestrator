package com.tim.workflow.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tim.workflow.orchestrator.domain.WorkflowExecution;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

}
