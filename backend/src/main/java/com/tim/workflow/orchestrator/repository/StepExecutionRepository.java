package com.tim.workflow.orchestrator.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;

public interface StepExecutionRepository extends JpaRepository<StepExecution, Long> {

    long countByStatus(StepExecutionStatus status);

    List<StepExecution> findByWorkflowExecutionIdOrderByStepIndexAsc(Long workflowExecutionId);

    Optional<StepExecution> findByWorkflowExecutionIdAndStepName(Long workflowExecutionId, String stepName);

    /**
     * Atomically claims a step for dispatch: {@code PENDING -> RUNNING} with {@code started_at}.
     *
     * @return number of rows updated (1 if claimed, 0 if another worker already claimed)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StepExecution s
            SET s.status = :running,
                s.startedAt = :now,
                s.updatedAt = :now
            WHERE s.id = :id AND s.status = :pending
            """)
    int claimPendingToRunning(
            @Param("id") Long id,
            @Param("now") Instant now,
            @Param("pending") StepExecutionStatus pending,
            @Param("running") StepExecutionStatus running
    );

    @Query("""
            SELECT s FROM StepExecution s
            WHERE s.status = :running
              AND s.k8sJobName IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM WorkflowExecution e
                  WHERE e.id = s.workflowExecutionId
                    AND e.status IN :execStatuses
              )
            """)
    List<StepExecution> findRunningWithK8sJobForActiveExecutions(
            @Param("running") StepExecutionStatus running,
            @Param("execStatuses") Collection<WorkflowExecutionStatus> execStatuses
    );
}
