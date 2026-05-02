package com.tim.workflow.orchestrator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tim.workflow.orchestrator.domain.ExecutionEvent;

public interface ExecutionEventRepository extends JpaRepository<ExecutionEvent, Long> {

    List<ExecutionEvent> findByWorkflowExecutionIdOrderByCreatedAtAsc(Long workflowExecutionId);
}
