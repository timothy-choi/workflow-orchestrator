package com.tim.workflow.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.api.internal.StepCallbackController;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.StepResultRequest;
import com.tim.workflow.orchestrator.dto.StepResultRequest.StepResultStatus;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.scheduler.WorkflowScheduler;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExecutionReliabilityIntegrationTest {

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
    private WorkflowScheduler workflowScheduler;

    @Test
    void failedCallback_withRetriesLeft_scheduleRetryWait() throws Exception {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(2);
        Long workflowId = createWorkflow(step);
        Long executionId = executionService.createExecution(workflowId).getId();

        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING).setStartedAt(Instant.now());
        stepExecutionRepository.save(se);

        postFailed(executionId, se.getId(), "boom");

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.RETRY_WAIT);
        assertThat(r.getSteps().get(0).getRetryCount()).isEqualTo(1);
        assertThat(r.getSteps().get(0).getAttempt()).isEqualTo(2);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.RUNNING);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_RETRY_SCHEDULED)
                .hasSize(1);
        assertThat(r.getEvents())
                .noneMatch(e -> e.getEventType() == ExecutionEventType.STEP_FAILED);
    }

    @Test
    void retryWait_whenDue_isPromotedAndRunsAgainInSameTickLocally() {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(2);
        Long workflowId = createWorkflow(step);
        Long executionId = executionService.createExecution(workflowId).getId();

        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RETRY_WAIT)
                .setRetryCount(1)
                .setAttempt(2)
                .setNextRetryAt(Instant.now().minusSeconds(5))
                .setUpdatedAt(Instant.now());
        stepExecutionRepository.save(se);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_RETRY_READY)
                .hasSize(1);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
    }

    @Test
    void failedCallback_noRetriesLeft_failsExecution() throws Exception {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(0);
        Long workflowId = createWorkflow(step);
        Long executionId = executionService.createExecution(workflowId).getId();

        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING).setStartedAt(Instant.now());
        stepExecutionRepository.save(se);

        postFailed(executionId, se.getId(), "fatal");

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(r.getFinishedAt()).isNotNull();
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_FAILED)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .hasSize(1);
        assertThat(r.getEvents().stream()
                .filter(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .findFirst()
                .orElseThrow()
                .getPayload())
                .contains("\"stepName\":\"only\"", "\"failureReason\":\"fatal\"");
    }

    @Test
    void runningStep_timeout_schedulesRetryWhenConfigured() {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(2);
        step.setTimeoutSeconds(1);
        Long workflowId = createWorkflow(step);
        Long executionId = executionService.createExecution(workflowId).getId();

        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING)
                .setStartedAt(Instant.now().minusSeconds(120))
                .setTimeoutSeconds(1)
                .setUpdatedAt(Instant.now());
        stepExecutionRepository.save(se);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.RETRY_WAIT);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_TIMED_OUT)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_RETRY_SCHEDULED)
                .hasSize(1);
    }

    @Test
    void runningStep_timeout_withNoRetriesLeft_failsExecution() {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(0);
        step.setTimeoutSeconds(1);
        Long workflowId = createWorkflow(step);
        Long executionId = executionService.createExecution(workflowId).getId();

        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING)
                .setStartedAt(Instant.now().minusSeconds(120))
                .setTimeoutSeconds(1)
                .setUpdatedAt(Instant.now());
        stepExecutionRepository.save(se);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_TIMED_OUT)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_FAILED)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .hasSize(1);
    }

    @Test
    void claimPending_secondDatabaseClaimDoesNothing() {
        WorkflowStepRequest step = step("only", List.of());
        Long workflowId = createWorkflow(step);
        Long executionId = executionService.createExecution(workflowId).getId();
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);

        Instant now = Instant.now();
        assertThat(stepExecutionRepository.claimPendingToRunning(
                se.getId(),
                now,
                StepExecutionStatus.PENDING,
                StepExecutionStatus.RUNNING
        )).isEqualTo(1);
        assertThat(stepExecutionRepository.claimPendingToRunning(
                se.getId(),
                now,
                StepExecutionStatus.PENDING,
                StepExecutionStatus.RUNNING
        )).isEqualTo(0);

        StepExecution updated = stepExecutionRepository.findById(se.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(StepExecutionStatus.RUNNING);
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

    private Long createWorkflow(WorkflowStepRequest step) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("reliability-" + System.nanoTime());
        req.setSteps(List.of(step));
        return workflowService.createWorkflow(req).getId();
    }

    private static WorkflowStepRequest step(String name, List<String> deps) {
        WorkflowStepRequest s = new WorkflowStepRequest();
        s.setName(name);
        s.setImage("busybox:latest");
        s.setCommand("echo ok");
        s.setDependencies(deps);
        return s;
    }
}
