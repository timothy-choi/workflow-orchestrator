package com.tim.workflow.orchestrator.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.service.ExecutionService;

@RestController
@RequestMapping("/executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    public ExecutionResponse createExecution(@RequestParam("workflowId") Long workflowId) {
        return executionService.createExecution(workflowId);
    }

    @GetMapping("/{executionId}")
    public ExecutionResponse getExecution(@PathVariable Long executionId) {
        return executionService.getExecution(executionId);
    }

    @PostMapping("/{executionId}/pause")
    public ExecutionResponse pauseExecution(@PathVariable Long executionId) {
        return executionService.pauseExecution(executionId);
    }

    @PostMapping("/{executionId}/resume")
    public ExecutionResponse resumeExecution(@PathVariable Long executionId) {
        return executionService.resumeExecution(executionId);
    }

    @PostMapping("/{executionId}/cancel")
    public ExecutionResponse cancelExecution(@PathVariable Long executionId) {
        return executionService.cancelExecution(executionId);
    }
}
