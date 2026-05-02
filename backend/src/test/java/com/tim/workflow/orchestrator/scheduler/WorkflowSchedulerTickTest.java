package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

@SpringBootTest
class WorkflowSchedulerTickTest {

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Test
    void linearWorkflow_executionEndsSucceededWithAllStepsSuccess() {
        Long workflowId = createWorkflow(
                step("A", List.of()),
                step("B", List.of("A")),
                step("C", List.of("B"))
        );

        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);

        ExecutionResponse result = executionService.getExecution(executionId);
        assertThat(result.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(result.getFinishedAt()).isNotNull();
        assertThat(result.getSteps()).hasSize(3);
        assertThat(result.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);

        assertThat(result.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_SUCCEEDED)
                .hasSize(1);
        assertThat(result.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_STARTED)
                .hasSize(3);
        assertThat(result.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_SUCCEEDED)
                .hasSize(3);
    }

    @Test
    void parallelIndependentSteps_completeInOneTick() {
        Long workflowId = createWorkflow(
                step("A", List.of()),
                step("B", List.of())
        );

        Long executionId = executionService.createExecution(workflowId).getId();
        workflowScheduler.processExecution(executionId);

        ExecutionResponse result = executionService.getExecution(executionId);
        assertThat(result.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(result.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);
    }

    @Test
    void fanInWorkflow_completesAfterMergeStep() {
        Long workflowId = createWorkflow(
                step("A", List.of()),
                step("B", List.of()),
                step("C", List.of("A", "B"))
        );

        Long executionId = executionService.createExecution(workflowId).getId();
        workflowScheduler.processExecution(executionId);

        ExecutionResponse result = executionService.getExecution(executionId);
        assertThat(result.getStatus()).isEqualTo(WorkflowExecutionStatus.SUCCEEDED);
        assertThat(result.getSteps()).extracting(s -> s.getStepName()).containsExactly("A", "B", "C");
        assertThat(result.getSteps()).allMatch(s -> s.getStatus() == StepExecutionStatus.SUCCESS);
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("scheduler-test-" + System.nanoTime());
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
