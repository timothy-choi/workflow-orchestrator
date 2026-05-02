package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

@SpringBootTest
@TestPropertySource(
        properties = {
                "workflow.scheduler.retry-backoff-base-seconds=0"
        }
)
class WorkflowSchedulerLocalExecutionTest {

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Test
    void exitOne_noRetries_failsExecutionWithoutRetryScheduled() {
        WorkflowStepRequest s = step("flaky", List.of());
        s.setCommand("exit 1");
        s.setMaxRetries(0);

        Long workflowId = createWorkflow(s);
        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_RETRY_SCHEDULED)
                .isEmpty();
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_FAILED)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .hasSize(1);
        assertThat(r.getEvents())
                .noneMatch(e -> e.getEventType() == ExecutionEventType.STEP_SUCCEEDED);
    }

    @Test
    void echoHello_succeedsWithStepSucceededEvent() {
        WorkflowStepRequest s = step("hello-step", List.of());
        s.setCommand("echo hello");

        Long workflowId = createWorkflow(s);
        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.SUCCESS);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_SUCCEEDED)
                .hasSize(1);
        assertThat(r.getEvents())
                .noneMatch(e -> e.getEventType() == ExecutionEventType.STEP_FAILED);
    }

    @Test
    void sleepTimeout_noRetries_recordsTimedOutAndFails() {
        WorkflowStepRequest s = step("slow", List.of());
        s.setCommand("sleep 10");
        s.setTimeoutSeconds(1);
        s.setMaxRetries(0);

        Long workflowId = createWorkflow(s);
        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
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
    void exitOne_maxRetriesTwo_emitsRetryScheduledThenFailsAfterRetries() {
        WorkflowStepRequest s = step("flaky", List.of());
        s.setCommand("exit 1");
        s.setMaxRetries(2);

        Long workflowId = createWorkflow(s);
        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);
        workflowScheduler.processExecution(executionId);
        workflowScheduler.processExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.FAILED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.FAILED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_RETRY_SCHEDULED)
                .hasSize(2);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_FAILED)
                .hasSize(1);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_FAILED)
                .hasSize(1);
    }

    private Long createWorkflow(WorkflowStepRequest step) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("local-exec-" + System.nanoTime());
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
