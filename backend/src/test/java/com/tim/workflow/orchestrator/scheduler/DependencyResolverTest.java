package com.tim.workflow.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;

class DependencyResolverTest {

    private final DependencyResolver resolver = new DependencyResolver();

    @Test
    void linearWorkflow_onlyFirstStepRunnableInitially() {
        CreateWorkflowRequest def = requestWithSteps(
                step("A", List.of()),
                step("B", List.of("A")),
                step("C", List.of("B"))
        );

        Map<String, StepExecution> byName = map(
                "A", pending("A"),
                "B", pending("B"),
                "C", pending("C")
        );

        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("A");

        byName.put("A", success("A"));
        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("B");

        byName.put("B", success("B"));
        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("C");
    }

    @Test
    void parallelIndependentSteps_bothRunnable() {
        CreateWorkflowRequest def = requestWithSteps(
                step("A", List.of()),
                step("B", List.of())
        );

        Map<String, StepExecution> byName = map(
                "A", pending("A"),
                "B", pending("B")
        );

        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("A", "B");
    }

    @Test
    void fanIn_stepCwaitsForAandB() {
        CreateWorkflowRequest def = requestWithSteps(
                step("A", List.of()),
                step("B", List.of()),
                step("C", List.of("A", "B"))
        );

        Map<String, StepExecution> byName = map(
                "A", pending("A"),
                "B", pending("B"),
                "C", pending("C")
        );

        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("A", "B");

        byName.put("A", success("A"));
        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("B");

        byName.put("B", success("B"));
        assertThat(resolver.resolveRunnable(def, byName)).extracting(WorkflowStepRequest::getName).containsExactly("C");
    }

    @Test
    void pendingStepBlockedWhenDependencyNotSuccessful() {
        CreateWorkflowRequest def = requestWithSteps(
                step("A", List.of()),
                step("B", List.of("A"))
        );

        Map<String, StepExecution> byName = map(
                "A", pending("A"),
                "B", pending("B")
        );

        byName.put("A", running("A"));
        assertThat(resolver.resolveRunnable(def, byName)).isEmpty();
    }

    private static CreateWorkflowRequest requestWithSteps(WorkflowStepRequest... steps) {
        CreateWorkflowRequest r = new CreateWorkflowRequest();
        r.setName("test-def");
        r.setSteps(List.of(steps));
        return r;
    }

    private static WorkflowStepRequest step(String name, List<String> deps) {
        WorkflowStepRequest s = new WorkflowStepRequest();
        s.setName(name);
        s.setImage("busybox:latest");
        s.setCommand("echo x");
        s.setDependencies(deps);
        return s;
    }

    private static Map<String, StepExecution> map(Object... keysAndValues) {
        Map<String, StepExecution> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], (StepExecution) keysAndValues[i + 1]);
        }
        return m;
    }

    private static StepExecution pending(String name) {
        return base(name).setStatus(StepExecutionStatus.PENDING);
    }

    private static StepExecution success(String name) {
        return base(name).setStatus(StepExecutionStatus.SUCCESS);
    }

    private static StepExecution running(String name) {
        return base(name).setStatus(StepExecutionStatus.RUNNING);
    }

    private static StepExecution base(String name) {
        return new StepExecution().setStepName(name).setStepIndex(0).setWorkflowExecutionId(1L);
    }
}
