package com.tim.workflow.orchestrator.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tim.workflow.orchestrator.dto.ExecutionEventResponse;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.ExecutionSummaryResponse;
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

    @GetMapping
    public List<ExecutionSummaryResponse> listExecutions(@RequestParam(defaultValue = "50") int limit) {
        return executionService.listRecentExecutions(limit);
    }

    @GetMapping("/{executionId}")
    public ExecutionResponse getExecution(@PathVariable Long executionId) {
        return executionService.getExecution(executionId);
    }

    @GetMapping("/{executionId}/events")
    public List<ExecutionEventResponse> listExecutionEvents(@PathVariable Long executionId) {
        return executionService.listExecutionEvents(executionId);
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
