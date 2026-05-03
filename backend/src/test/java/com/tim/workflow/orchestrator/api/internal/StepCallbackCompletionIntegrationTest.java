package com.tim.workflow.orchestrator.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.StepResultRequest;
import com.tim.workflow.orchestrator.dto.StepResultRequest.StepResultStatus;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StepCallbackCompletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private StepExecutionRepository stepExecutionRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void callbackSuccess_whenAllStepsRunning_thenExecutionSucceeded() throws Exception {
        Long workflowId = createWorkflow(
                step("a", List.of()),
                step("b", List.of())
        );
        Long executionId = executionService.createExecution(workflowId).getId();

        stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).forEach(s -> {
            s.setStatus(StepExecutionStatus.RUNNING);
            stepExecutionRepository.save(s);
        });

        postSuccess(executionId, stepExecutionRepository
                .findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId());
        postSuccess(executionId, stepExecutionRepository
                .findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(1).getId());

        ExecutionResponse result = executionService.getExecution(executionId);
        assertThat(result.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);
        assertThat(result.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_SUCCEEDED)
                .hasSize(1);
    }

    @Test
    void callbackSuccess_duplicatePost_isIdempotentForStepSucceeded() throws Exception {
        Long workflowId = createWorkflow(step("only", List.of()));
        Long executionId = executionService.createExecution(workflowId).getId();

        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();
        stepExecutionRepository.findById(stepId).ifPresent(s -> {
            s.setStatus(StepExecutionStatus.RUNNING);
            stepExecutionRepository.save(s);
        });

        postSuccess(executionId, stepId);
        postSuccess(executionId, stepId);

        ExecutionResponse result = executionService.getExecution(executionId);
        assertThat(result.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(result.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_SUCCEEDED)
                .hasSize(1);
        assertThat(result.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.CALLBACK_RECEIVED)
                .hasSize(1);
    }

    @Test
    void callbackSuccess_incrementsCallbackReceivedSuccessMetric() throws Exception {
        Long workflowId = createWorkflow(step("metric-success", List.of()));
        Long executionId = executionService.createExecution(workflowId).getId();
        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();
        stepExecutionRepository.findById(stepId).ifPresent(s -> {
            s.setStatus(StepExecutionStatus.RUNNING);
            stepExecutionRepository.save(s);
        });

        double before = callbackReceivedCount("SUCCESS");
        postSuccess(executionId, stepId);
        assertThat(callbackReceivedCount("SUCCESS")).isGreaterThan(before);
    }

    @Test
    void callbackFailed_incrementsCallbackReceivedFailedMetric() throws Exception {
        Long workflowId = createWorkflow(step("metric-failed", List.of()));
        Long executionId = executionService.createExecution(workflowId).getId();
        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();
        stepExecutionRepository.findById(stepId).ifPresent(s -> {
            s.setStatus(StepExecutionStatus.RUNNING);
            s.setMaxRetries(0);
            stepExecutionRepository.save(s);
        });

        double before = callbackReceivedCount("FAILED");
        postFailed(executionId, stepId, "boom");
        assertThat(callbackReceivedCount("FAILED")).isGreaterThan(before);
    }

    @Test
    void callbackWhenCancelled_doesNotIncrementSuccessCallbackMetric() throws Exception {
        Long workflowId = createWorkflow(step("metric-ignored", List.of()));
        Long executionId = executionService.createExecution(workflowId).getId();
        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();
        stepExecutionRepository.findById(stepId).ifPresent(s -> {
            s.setStatus(StepExecutionStatus.RUNNING);
            stepExecutionRepository.save(s);
        });

        mockMvc.perform(post("/executions/" + executionId + "/cancel"))
                .andExpect(status().isOk());

        double callbacksBefore = callbackReceivedCount("SUCCESS");

        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(stepId);
        body.setStatus(StepResultStatus.SUCCESS);
        body.setMessage("finished");

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(callbackReceivedCount("SUCCESS")).isEqualTo(callbacksBefore);
    }

    @Test
    void callbackWithWrongToken_returnsUnauthorized() throws Exception {
        Long workflowId = createWorkflow(step("only", List.of()));
        Long executionId = executionService.createExecution(workflowId).getId();
        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();

        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(stepId);
        body.setStatus(StepResultStatus.SUCCESS);
        body.setMessage("ok");

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    private void postSuccess(Long executionId, long stepExecutionId) throws Exception {
        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(stepExecutionId);
        body.setStatus(StepResultStatus.SUCCESS);
        body.setMessage("finished");

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }

    private void postFailed(Long executionId, long stepExecutionId, String message) throws Exception {
        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(stepExecutionId);
        body.setStatus(StepResultStatus.FAILED);
        body.setMessage(message);

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("callback-test-" + System.nanoTime());
        req.setSteps(List.of(steps));
        return workflowService.createWorkflow(req).getId();
    }

    private static WorkflowStepRequest step(String name, List<String> deps) {
        WorkflowStepRequest s = new WorkflowStepRequest();
        s.setName(name);
        s.setImage("busybox:latest");
        s.setCommand("echo " + name);
        s.setDependencies(deps);
        return s;
    }

    private double callbackReceivedCount(String status) {
        var counter = meterRegistry.find("workflow_callbacks_received").tag("status", status).counter();
        return counter != null ? counter.count() : 0.0;
    }
}
