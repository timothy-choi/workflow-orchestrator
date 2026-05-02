package com.tim.workflow.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
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
import com.tim.workflow.orchestrator.k8s.KubernetesJobDeleteOutcome;
import com.tim.workflow.orchestrator.k8s.KubernetesJobDeleter;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "orchestrator.execution.mode=kubernetes")
class KubernetesCancelIntegrationTest {

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

    @MockBean
    private ApiClient kubernetesApiClient;

    @MockBean
    private BatchV1Api kubernetesBatchV1Api;

    @MockBean
    private KubernetesJobDeleter kubernetesJobDeleter;

    @BeforeEach
    void defaultSuccessfulDelete() {
        reset(kubernetesJobDeleter);
        when(kubernetesJobDeleter.deleteJobBestEffort(anyString())).thenReturn(KubernetesJobDeleteOutcome.deleted());
    }

    @Test
    void cancel_runningExecution_returns200() throws Exception {
        long executionId = runningExecutionWithK8sJob("wf-cancel-200");

        mockMvc.perform(post("/executions/" + executionId + "/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_marksExecutionCancelled() {
        long executionId = runningExecutionWithK8sJob("wf-cancel-exec");

        executionService.cancelExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.CANCELLED);
        assertThat(r.isCancelRequested()).isTrue();
        assertThat(r.getCancelledAt()).isNotNull();
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.EXECUTION_CANCELLED)
                .hasSize(1);
    }

    @Test
    void cancel_marksRunningStepCancelled() {
        long executionId = runningExecutionWithK8sJob("wf-cancel-step");

        executionService.cancelExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getSteps()).hasSize(1);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.CANCELLED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.STEP_CANCELLED)
                .hasSize(1);
    }

    @Test
    void cancel_ignoresMissingKubernetesJob_recordsJobNotFound() {
        when(kubernetesJobDeleter.deleteJobBestEffort(anyString()))
                .thenReturn(KubernetesJobDeleteOutcome.notFound(404, "not found"));

        long executionId = runningExecutionWithK8sJob("wf-cancel-missing-job");

        executionService.cancelExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.CANCELLED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.CANCELLED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.JOB_NOT_FOUND)
                .hasSize(1);
    }

    @Test
    void cancel_non404DeleteFailure_recordsJobAlreadyFinished_andStillCancels() {
        when(kubernetesJobDeleter.deleteJobBestEffort(anyString()))
                .thenReturn(KubernetesJobDeleteOutcome.deleteFailed(503, "unavailable"));

        long executionId = runningExecutionWithK8sJob("wf-cancel-delete-fail");

        executionService.cancelExecution(executionId);

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.CANCELLED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.JOB_ALREADY_FINISHED)
                .hasSize(1);
    }

    @Test
    void callback_afterCancel_returns200AndPreservesCancelledState() throws Exception {
        long executionId = runningExecutionWithK8sJob("wf-cancel-callback");
        StepExecution step = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);

        executionService.cancelExecution(executionId);

        StepResultRequest body = new StepResultRequest();
        body.setExecutionId(executionId);
        body.setStepExecutionId(step.getId());
        body.setStatus(StepResultStatus.SUCCESS);
        body.setMessage("late");

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ExecutionResponse r = executionService.getExecution(executionId);
        assertThat(r.getStatus()).isEqualTo(WorkflowExecutionStatus.CANCELLED);
        assertThat(r.getSteps().get(0).getStatus()).isEqualTo(StepExecutionStatus.CANCELLED);
        assertThat(r.getEvents())
                .filteredOn(e -> e.getEventType() == ExecutionEventType.CALLBACK_IGNORED_EXECUTION_CANCELLED)
                .hasSize(1);
    }

    private long runningExecutionWithK8sJob(String workflowNameSuffix) {
        WorkflowStepRequest step = step("only", List.of());
        Long workflowId = createWorkflow(workflowNameSuffix, step);
        Long executionId = executionService.createExecution(workflowId).getId();
        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING)
                .setStartedAt(Instant.now())
                .setK8sJobName("job-" + executionId + "-" + se.getId());
        stepExecutionRepository.save(se);
        return executionId;
    }

    private Long createWorkflow(String nameSuffix, WorkflowStepRequest step) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("k8s-cancel-" + nameSuffix + "-" + System.nanoTime());
        req.setSteps(List.of(step));
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
