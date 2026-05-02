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
}
