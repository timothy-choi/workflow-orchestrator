package com.tim.workflow.orchestrator.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;

import jakarta.persistence.LockModeType;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    List<WorkflowExecution> findByStatusIn(Collection<WorkflowExecutionStatus> statuses);

    /**
     * Active executions eligible for scheduling (created but not yet finished workflow lifecycle).
     */
    @Query("SELECT e FROM WorkflowExecution e WHERE e.status IN :statuses ORDER BY e.id ASC")
    List<WorkflowExecution> findActiveForScheduler(@Param("statuses") Collection<WorkflowExecutionStatus> statuses);

    long countByStatusIn(Collection<WorkflowExecutionStatus> statuses);

    List<WorkflowExecution> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM WorkflowExecution e WHERE e.id = :id")
    Optional<WorkflowExecution> findLockedById(@Param("id") Long id);
}
