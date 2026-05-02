package com.tim.workflow.orchestrator.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tim.workflow.orchestrator.domain.StepExecution;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;

@Component
public class DependencyResolver {

    /**
     * Returns workflow step definitions that may run now: matching {@link StepExecution} is
     * {@link StepExecutionStatus#PENDING} and every dependency step name maps to {@link StepExecutionStatus#SUCCESS}.
     * Order follows the workflow definition (stable scheduling).
     */
    public List<WorkflowStepRequest> resolveRunnable(
            CreateWorkflowRequest definition,
            Map<String, StepExecution> stepsByName
    ) {
        if (definition.getSteps() == null || definition.getSteps().isEmpty()) {
            return List.of();
        }

        Map<String, StepExecutionStatus> statusByName = new HashMap<>();
        stepsByName.forEach((name, se) -> statusByName.put(name, se.getStatus()));

        List<WorkflowStepRequest> runnable = new ArrayList<>();
        for (WorkflowStepRequest stepDef : definition.getSteps()) {
            StepExecution entity = stepsByName.get(stepDef.getName());
            if (entity == null || entity.getStatus() != StepExecutionStatus.PENDING) {
                continue;
            }
            if (dependenciesSatisfied(stepDef.getDependencies(), statusByName)) {
                runnable.add(stepDef);
            }
        }
        return runnable;
    }

    private boolean dependenciesSatisfied(
            List<String> dependencies,
            Map<String, StepExecutionStatus> statusByName
    ) {
        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }
        for (String dep : dependencies) {
            StepExecutionStatus st = statusByName.get(dep);
            if (st != StepExecutionStatus.SUCCESS) {
                return false;
            }
        }
        return true;
    }
}
