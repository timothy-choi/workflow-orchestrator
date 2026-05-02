package com.tim.workflow.orchestrator.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.domain.Workflow;
import com.tim.workflow.orchestrator.domain.WorkflowVersion;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.WorkflowDetailResponse;
import com.tim.workflow.orchestrator.dto.WorkflowResponse;
import com.tim.workflow.orchestrator.repository.WorkflowRepository;
import com.tim.workflow.orchestrator.repository.WorkflowVersionRepository;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ObjectMapper objectMapper;

    public WorkflowService(
            WorkflowRepository workflowRepository,
            WorkflowVersionRepository workflowVersionRepository,
            ObjectMapper objectMapper
    ) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        Workflow workflow = new Workflow()
                .setName(request.getName())
                .setDescription(request.getDescription())
                .setCurrentVersion(1);

        Workflow savedWorkflow = workflowRepository.save(workflow);

        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid workflow definition", e);
        }

        WorkflowVersion version = new WorkflowVersion()
                .setWorkflowId(savedWorkflow.getId())
                .setVersionNumber(1)
                .setDefinitionJson(definitionJson);

        workflowVersionRepository.save(version);

        return toResponse(savedWorkflow);
    }

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private WorkflowResponse toResponse(Workflow workflow) {
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getCurrentVersion()
        );
    }

    public WorkflowDetailResponse getWorkflow(Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        WorkflowVersion version = workflowVersionRepository
                .findByWorkflowIdAndVersionNumber(workflow.getId(), workflow.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("Workflow version not found"));

        return new WorkflowDetailResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getCurrentVersion(),
                version.getDefinitionJson()
        );
    }
}