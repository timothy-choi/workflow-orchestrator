package com.tim.workflow.orchestrator.api;

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
class ExecutionControlIntegrationTest {

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

    @Test
    void pause_preventsDispatch_resumeAllowsCompletion() {
        WorkflowStepRequest a = step("A", List.of());
        WorkflowStepRequest b = step("B", List.of());
        Long workflowId = createWorkflow(a, b);
        Long executionId = executionService.createExecution(workflowId).getId();

        executionService.pauseExecution(executionId);
        workflowScheduler.processExecution(executionId);

        ExecutionResponse pausedState = executionService.getExecution(executionId);
        assertThat(pausedState.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(pausedState.getPausedAt()).isNotNull();
        assertThat(pausedState.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.PENDING);

        executionService.resumeExecution(executionId);
        workflowScheduler.processExecution(executionId);

        ExecutionResponse done = executionService.getExecution(executionId);
        assertThat(done.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(done.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);
        assertThat(done.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_PAUSED)
                .hasSize(1);
        assertThat(done.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_RESUMED)
                .hasSize(1);
    }

    @Test
    void cancel_marksPendingStepsCancelled() {
        WorkflowStepRequest a = step("A", List.of());
        WorkflowStepRequest b = step("B", List.of());
        Long workflowId = createWorkflow(a, b);
        Long executionId = executionService.createExecution(workflowId).getId();

        executionService.cancelExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.CANCELLED);
        assertThat(r.isCancelRequested()).isTrue();
        assertThat(r.getCancelledAt()).isNotNull();
        assertThat(r.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.CANCELLED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_CANCELLED)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_CANCELLED)
                .hasSize(2);
    }

    @Test
    void callback_afterCancel_returnsOk_andIgnores() throws Exception {
        WorkflowStepRequest only = step("only", List.of());
        Long workflowId = createWorkflow(only);
        Long executionId = executionService.createExecution(workflowId).getId();
        long stepId = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0).getId();

        stepExecutionRepository.findById(stepId).ifPresent(s -> {
            s.setStatus(StepExecutionStatus.RUNNING);
            stepExecutionRepository.save(s);
        });

        executionService.cancelExecution(executionId);

        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(stepId);
        body.setStatus(StepResultStatus.SUCCESS);
        body.setMessage("late");

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.CALLBACK_IGNORED_STEP_CANCELLED
                        || e.getEventType() == ExecutionEventType.CALLBACK_IGNORED_EXECUTION_CANCELLED)
                .isNotEmpty();
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.CANCELLED);
    }

    @Test
    void manualRetry_failedStep_becomesPending_andUnfailsExecution() throws Exception {
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

        ExecutionResponse failed = executionService.getExecution(executionId);
        assertThat(failed.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(failed.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
        assertThat(failed.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_FAILED)
                .hasSize(1);
        assertThat(failed.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .hasSize(1);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isOk());

        ExecutionResponse retried = executionService.getExecution(executionId);
        assertThat(retried.getStatus()).isEqualTo(WorkflowExecutionStatus.RUNNING);
        assertThat(retried.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
        assertThat(retried.getSteps().get(0).getAttempt()).isGreaterThan(failed.getSteps().get(0).getAttempt());
        assertThat(retried.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .hasSize(1);
        assertThat(retried.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_MANUAL_RETRY_REQUESTED)
                .hasSize(1);
    }

    @Test
    void scheduler_tick_skipsPausedExecution() {
        WorkflowStepRequest a = step("A", List.of());
        Long workflowId = createWorkflow(a);
        Long executionId = executionService.createExecution(workflowId).getId();
        executionService.pauseExecution(executionId);

        workflowScheduler.tick();

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
    }

    @Test
    void scheduler_tick_skipsCancelledExecution() {
        WorkflowStepRequest a = step("A", List.of());
        Long workflowId = createWorkflow(a);
        Long executionId = executionService.createExecution(workflowId).getId();
        executionService.cancelExecution(executionId);

        workflowScheduler.tick();

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.CANCELLED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.CANCELLED);
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("control-test-" + System.nanoTime());
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
