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
 * No test-class {@code @Transactional}: scheduler and callbacks use their own transactions and must
 * see committed execution rows (same as production).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PauseResumeWorkflowIntegrationTest {

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
    void pauseCreated_preventsFirstStepDispatch_andCreatesPausedEvent() {
        WorkflowStepRequest a = step("A", List.of());
        WorkflowStepRequest b = step("B", List.of());
        Long workflowId = createWorkflow(a, b);
        Long executionId = executionService.createExecution(workflowId).getId();

        ExecutionResponse paused = executionService.pauseExecution(executionId);
        assertThat(paused.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(paused.isPauseRequested()).isTrue();
        assertThat(paused.getPausedAt()).isNotNull();
        assertThat(paused.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_PAUSED)
                .hasSize(1);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse after = executionService.getExecution(executionId);
        assertThat(after.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(after.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.PENDING);
    }

    @Test
    void pauseRunning_preventsDependentNextStepDispatch() {
        Long executionId = createLinearExecutionWithArunningBpending();

        ExecutionResponse paused = executionService.pauseExecution(executionId);
        assertThat(paused.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(paused.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_PAUSED)
                .hasSize(1);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse stuck = executionService.getExecution(executionId);
        assertThat(stuck.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(stuck.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.RUNNING);
        assertThat(stuck.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
    }

    @Test
    void resumePaused_allowsPendingStepDispatch_andCreatesResumedEvent() {
        Long executionId = createPausedExecutionWithASuccessBpending();

        workflowScheduler.processExecution(executionId);
        ExecutionResponse whilePaused = executionService.getExecution(executionId);
        assertThat(whilePaused.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(whilePaused.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);

        ExecutionResponse resumed = executionService.resumeExecution(executionId);
        assertThat(resumed.getStatus()).isEqualTo(WorkflowExecutionStatus.RUNNING);
        assertThat(resumed.isPauseRequested()).isFalse();
        assertThat(resumed.getPausedAt()).isNull();
        assertThat(resumed.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_RESUMED)
                .hasSize(1);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse done = executionService.getExecution(executionId);
        assertThat(done.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(done.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);
    }

    @Test
    void callbackWhilePaused_completesRunningStep_butDoesNotDispatchNextUntilResume() throws Exception {
        Long executionId = createLinearExecutionWithArunningBpending();
        StepExecution stepA = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);

        executionService.pauseExecution(executionId);

        postSuccess(executionId, stepA.getId());

        ExecutionResponse mid = executionService.getExecution(executionId);
        assertThat(mid.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(mid.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(mid.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);

        workflowScheduler.processExecution(executionId);
        ExecutionResponse stillWaiting = executionService.getExecution(executionId);
        assertThat(stillWaiting.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(stillWaiting.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);

        executionService.resumeExecution(executionId);
        workflowScheduler.processExecution(executionId);

        ExecutionResponse done = executionService.getExecution(executionId);
        assertThat(done.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(done.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
    }

    @Test
    void pause_afterStepASuccessCallback_beforeSchedulerDispatchesB_keepsBpendingAndPaused() throws Exception {
        Long workflowId = createWorkflow(step("A", List.of()), step("B", List.of("A")));
        Long executionId = executionService.createExecution(workflowId).getId();

        WorkflowExecution ex = workflowExecutionRepository.findById(executionId).orElseThrow();
        ex.setStatus(WorkflowExecutionStatus.RUNNING);
        workflowExecutionRepository.save(ex);

        List<StepExecution> steps = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        steps.get(0).setStatus(StepExecutionStatus.RUNNING).setStartedAt(Instant.now());
        steps.get(1).setStatus(StepExecutionStatus.PENDING);
        stepExecutionRepository.save(steps.get(0));
        stepExecutionRepository.save(steps.get(1));

        postSuccess(executionId, steps.get(0).getId());

        ExecutionResponse afterA = executionService.getExecution(executionId);
        assertThat(afterA.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(afterA.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
        assertThat(afterA.getStatus()).isEqualTo(WorkflowExecutionStatus.RUNNING);

        executionService.pauseExecution(executionId);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse fin = executionService.getExecution(executionId);
        assertThat(fin.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(fin.isPauseRequested()).isTrue();
        assertThat(fin.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
        assertThat(fin.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_PAUSED)
                .hasSize(1);
    }

    @Test
    void pauseAndSchedulerConcurrent_rowLockSerializesPauseBeforeDispatch() throws Exception {
        Long executionId = createLinearExecutionWithArunningBpending();

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<?> pauseDone = pool.submit(() -> {
                barrier.await();
                executionService.pauseExecution(executionId);
                return null;
            });
            java.util.concurrent.Future<?> schedDone = pool.submit(() -> {
                barrier.await();
                workflowScheduler.processExecution(executionId);
                return null;
            });
            pauseDone.get(60, java.util.concurrent.TimeUnit.SECONDS);
            schedDone.get(60, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(r.isPauseRequested()).isTrue();
        assertThat(r.getSteps().get(1).getStatus()).isEqualTo(StepExecutionStatus.PENDING);
    }

    @Test
    void callbackWhilePaused_whenOnlyStepFinishes_staysPausedUntilResume() throws Exception {
        Long workflowId = createWorkflow(step("only", List.of()));
        Long executionId = executionService.createExecution(workflowId).getId();
        StepExecution step = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);

        WorkflowExecution ex = workflowExecutionRepository.findById(executionId).orElseThrow();
        ex.setStatus(WorkflowExecutionStatus.RUNNING);
        workflowExecutionRepository.save(ex);
        step.setStatus(StepExecutionStatus.RUNNING).setStartedAt(Instant.now());
        stepExecutionRepository.save(step);

        executionService.pauseExecution(executionId);

        postSuccess(executionId, step.getId());

        ExecutionResponse afterCb = executionService.getExecution(executionId);
        assertThat(afterCb.getStatus()).isEqualTo(WorkflowExecutionStatus.PAUSED);
        assertThat(afterCb.isPauseRequested()).isTrue();
        assertThat(afterCb.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);

        executionService.resumeExecution(executionId);
        workflowScheduler.processExecution(executionId);

        ExecutionResponse done = executionService.getExecution(executionId);
        assertThat(done.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(done.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(done.isPauseRequested()).isFalse();
        assertThat(done.getPausedAt()).isNull();
    }

    private Long createPausedExecutionWithASuccessBpending() {
        Long workflowId = createWorkflow(step("A", List.of()), step("B", List.of("A")));
        Long executionId = executionService.createExecution(workflowId).getId();

        WorkflowExecution ex = workflowExecutionRepository.findById(executionId).orElseThrow();
        ex.setStatus(WorkflowExecutionStatus.RUNNING);
        workflowExecutionRepository.save(ex);

        Instant now = Instant.now();
        List<StepExecution> steps = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        steps.get(0).setStatus(StepExecutionStatus.SUCCESS)
                .setStartedAt(now)
                .setFinishedAt(now)
                .setUpdatedAt(now);
        steps.get(1).setStatus(StepExecutionStatus.PENDING);
        stepExecutionRepository.save(steps.get(0));
        stepExecutionRepository.save(steps.get(1));

        executionService.pauseExecution(executionId);
        return executionId;
    }

    private Long createLinearExecutionWithArunningBpending() {
        Long workflowId = createWorkflow(step("A", List.of()), step("B", List.of("A")));
        Long executionId = executionService.createExecution(workflowId).getId();

        WorkflowExecution ex = workflowExecutionRepository.findById(executionId).orElseThrow();
        ex.setStatus(WorkflowExecutionStatus.RUNNING);
        workflowExecutionRepository.save(ex);

        List<StepExecution> steps = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId);
        steps.get(0).setStatus(StepExecutionStatus.RUNNING).setStartedAt(Instant.now());
        steps.get(1).setStatus(StepExecutionStatus.PENDING);
        stepExecutionRepository.save(steps.get(0));
        stepExecutionRepository.save(steps.get(1));

        return executionId;
    }

    private void postSuccess(Long executionId, long stepExecutionId) throws Exception {
        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(stepExecutionId);
        body.setStatus(StepResultStatus.SUCCESS);
        body.setMessage("done");

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("pause-resume-" + System.nanoTime());
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
