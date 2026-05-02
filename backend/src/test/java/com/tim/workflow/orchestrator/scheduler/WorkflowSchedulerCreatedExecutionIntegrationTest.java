package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tim.workflow.orchestrator.domain.ExecutionEventType;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.domain.WorkflowExecution;
import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.repository.WorkflowExecutionRepository;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

/**
 * Uses committed transactions (no test-class {@code @Transactional}) so repository calls in the
 * scheduler run in their own transactions and see persisted executions.
 * <p>
 * Asserts the same {@link WorkflowExecutionRepository#findActiveForScheduler} query used by
 * {@link WorkflowScheduler#runPollCycle()}, then runs {@link WorkflowScheduler#processExecution(Long)}
 * as each tick iteration does (avoids processing unrelated rows in a shared dev database).
 */
@SpringBootTest
class WorkflowSchedulerCreatedExecutionIntegrationTest {

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Autowired
    private WorkflowExecutionRepository workflowExecutionRepository;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Test
    void schedulerActiveQuery_findsCreatedExecution_thenProcessExecution_promotesAndDispatchesFirstStep() {
        Long workflowId = createWorkflow(
                step("A", List.of()),
                step("B", List.of("A"))
        );

        Long executionId = executionService.createExecution(workflowId).getId();

        ExecutionResponse created = executionService.getExecution(executionId);
        assertThat(created.getStatus()).isEqualTo(WorkflowExecutionStatus.CREATED);
        assertThat(created.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.PENDING);

        assertThat(workflowExecutionRepository.findActiveForScheduler(List.of(
                        WorkflowExecutionStatus.CREATED,
                        WorkflowExecutionStatus.RUNNING)))
                .extracting(WorkflowExecution::getId)
                .contains(executionId);

        workflowScheduler.processExecution(executionId);

        ExecutionResponse after = executionService.getExecution(executionId);
        assertThat(after.getStatus()).isNotEqualTo(WorkflowExecutionStatus.CREATED);
        assertThat(after.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_STARTED)
                .isNotEmpty();
        assertThat(after.getSteps().get(0).getStatus()).isNotEqualTo(StepExecutionStatus.PENDING);
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("scheduler-created-" + System.nanoTime());
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
