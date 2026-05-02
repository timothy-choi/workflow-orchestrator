package com.tim.workflow.orchestrator.api.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tim.workflow.orchestrator.dto.StepResultRequest;
import com.tim.workflow.orchestrator.service.StepCallbackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal")
public class StepCallbackController {

    public static final String CALLBACK_TOKEN_HEADER = "X-Callback-Token";

    private final StepCallbackService stepCallbackService;

    public StepCallbackController(StepCallbackService stepCallbackService) {
        this.stepCallbackService = stepCallbackService;
    }

    @PostMapping("/step-results")
    public ResponseEntity<Void> acceptStepResult(
            @Valid @RequestBody StepResultRequest body,
            @RequestHeader(value = CALLBACK_TOKEN_HEADER, required = false) String callbackToken
    ) {
        stepCallbackService.handleStepResult(body, callbackToken);
        return ResponseEntity.accepted().build();
    }
}
