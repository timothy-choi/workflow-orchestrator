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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.api.internal.StepCallbackController;
import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.StepResultRequest;
import com.tim.workflow.orchestrator.dto.StepResultRequest.StepResultStatus;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;
import com.tim.workflow.orchestrator.scheduler.WorkflowScheduler;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

/**
 * Manual retry touches execution lock and scheduler visibility; no test-class {@code @Transactional}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StepExecutionManualRetryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Autowired
    private StepExecutionRepository stepExecutionRepository;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Test
    void manualRetry_multiStepFailure_priorStepStaysSuccess_retryClearsFailureFields() throws Exception {
        WorkflowStepRequest a = step("A", List.of());
        WorkflowStepRequest b = step("B", List.of("A"));
        b.setCommand("exit 1");
        b.setMaxRetries(0);
        Long workflowId = createWorkflow(a, b);
        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);

        ExecutionResponse failed = executionService.getExecution(executionId);
        assertThat(failed.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(failed.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(failed.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
        assertThat(failed.getSteps().get(1).getFailureReason()).isNotBlank();
        long stepBId = failed.getSteps().get(1).getId();
        int attemptBefore = failed.getSteps().get(1).getAttempt();

        mockMvc.perform(post("/step-executions/" + stepBId + "/retry"))
                .andExpect(status().isOk());

        ExecutionResponse queued = executionService.getExecution(executionId);
        assertThat(queued.getStatus()).isEqualTo(WorkflowExecutionStatus.RUNNING);
        assertThat(queued.getFinishedAt()).isNull();
        assertThat(queued.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(queued.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
        assertThat(queued.getSteps().get(1).getAttempt()).isEqualTo(attemptBefore + 1);
        assertThat(queued.getSteps().get(1).getFailureReason()).isNull();
        assertThat(queued.getSteps().get(1).getRetryCount()).isEqualTo(failed.getSteps().get(1).getRetryCount());
        assertThat(queued.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_MANUAL_RETRY_REQUESTED)
                .hasSize(1);

        workflowScheduler.processExecution(executionId);
        ExecutionResponse afterRun = executionService.getExecution(executionId);
        assertThat(afterRun.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(afterRun.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
    }

    @Test
    void manualRetry_cancelledStep_returns409() throws Exception {
        Long executionId = freshExecutionWithSteps(step("only", List.of()));
        WorkflowExecution ex = workflowExecutionRepository.findById(executionId).orElseThrow();
        ex.setStatus(WorkflowExecutionStatus.RUNNING);
        workflowExecutionRepository.save(ex);
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.CANCELLED);
        stepExecutionRepository.save(se);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isConflict());
    }

    @Test
    void manualRetry_pendingStep_returns409() throws Exception {
        Long executionId = freshExecutionWithSteps(step("only", List.of()));
        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();

        mockMvc.perform(post("/step-executions/" + stepId + "/retry"))
                .andExpect(status().isConflict());
    }

    @Test
    void manualRetry_runningStep_returns409() throws Exception {
        Long executionId = freshExecutionWithSteps(step("only", List.of()));
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING).setStartedAt(Instant.now());
        stepExecutionRepository.save(se);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isConflict());
    }

    @Test
    void manualRetry_successStep_returns409() throws Exception {
        Long executionId = freshExecutionWithSteps(step("only", List.of()));
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        Instant now = Instant.now();
        se.setStatus(StepExecutionStatus.SUCCESS).setStartedAt(now).setFinishedAt(now);
        stepExecutionRepository.save(se);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isConflict());
    }

    @Test
    void manualRetry_retryWaitStep_returns409() throws Exception {
        WorkflowStepRequest s = step("only", List.of());
        s.setMaxRetries(2);
        Long executionId = freshExecutionWithSteps(s);
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RETRY_WAIT)
                .setRetryCount(1)
                .setAttempt(2)
                .setNextRetryAt(Instant.now().plusSeconds(3600))
                .setFailureReason("wait");
        stepExecutionRepository.save(se);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isConflict());
    }

    @Test
    void manualRetry_cancelledExecution_returns409() throws Exception {
        WorkflowStepRequest only = step("only", List.of());
        only.setMaxRetries(0);
        Long workflowId = createWorkflow(only);
        Long executionId = executionService.createExecution(workflowId).getId();
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING);
        stepExecutionRepository.save(se);

        StepResultRequest failBody = new StepResultRequest();
        failBody.setExecutionId(executionId);
        failBody.setStepExecutionId(se.getId());
        failBody.setStatus(StepResultStatus.FAILED);
        failBody.setMessage("bad");
        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failBody)))
                .andExpect(status().isAccepted());

        executionService.cancelExecution(executionId);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isConflict());
    }

    private Long freshExecutionWithSteps(WorkflowStepRequest step) {
        Long workflowId = createWorkflow(step);
        return executionService.createExecution(workflowId).getId();
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("manual-retry-" + System.nanoTime());
        req.setSteps(List.of(steps));
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
