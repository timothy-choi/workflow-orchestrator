package com.tim.workflow.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tim.workflow.orchestrator.domain.Workflow;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

}