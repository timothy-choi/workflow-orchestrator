package com.tim.workflow.orchestrator.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;

import jakarta.persistence.LockModeType;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    List<WorkflowExecution> findByStatusIn(Collection<WorkflowExecutionStatus> statuses);

    long countByStatusIn(Collection<WorkflowExecutionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM WorkflowExecution e WHERE e.id = :id")
    Optional<WorkflowExecution> findLockedById(@Param("id") Long id);
}
