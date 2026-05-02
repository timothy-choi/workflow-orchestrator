package com.tim.workflow.orchestrator.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.service.ExecutionService;

@RestController
@RequestMapping("/step-executions")
public class StepExecutionController {

    private final ExecutionService executionService;

    public StepExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/{stepExecutionId}/retry")
    public ExecutionResponse retryFailedStep(@PathVariable Long stepExecutionId) {
        return executionService.manualRetryFailedStep(stepExecutionId);
    }
}
