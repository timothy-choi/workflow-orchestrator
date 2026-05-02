package com.tim.workflow.orchestrator.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.WorkflowDetailResponse;
import com.tim.workflow.orchestrator.dto.WorkflowResponse;
import com.tim.workflow.orchestrator.service.WorkflowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public WorkflowResponse createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflowService.createWorkflow(request);
    }

    @GetMapping
    public List<WorkflowResponse> listWorkflows() {
        return workflowService.listWorkflows();
    }

    @GetMapping("/{workflowId}")
    public WorkflowDetailResponse getWorkflow(@PathVariable Long workflowId) {
        return workflowService.getWorkflow(workflowId);
    }
}