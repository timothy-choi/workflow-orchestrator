package com.tim.workflow.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.tim.workflow.orchestrator.k8s.KubernetesJobDeleter;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.scheduler.WorkflowScheduler;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api.APIcreateNamespacedJobRequest;
import io.kubernetes.client.openapi.models.V1Job;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "orchestrator.execution.mode=kubernetes")
class StepExecutionManualRetryKubernetesIntegrationTest {

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

    @MockBean
    private ApiClient kubernetesApiClient;

    @MockBean
    private BatchV1Api kubernetesBatchV1Api;

    @MockBean
    private KubernetesJobDeleter kubernetesJobDeleter;

    @BeforeEach
    void stubJobCreate() throws Exception {
        APIcreateNamespacedJobRequest createReq = mock(APIcreateNamespacedJobRequest.class);
        when(createReq.execute()).thenReturn(new V1Job());
        when(kubernetesBatchV1Api.createNamespacedJob(anyString(), any(V1Job.class))).thenReturn(createReq);
    }

    @Test
    void manualRetry_afterKubernetesFailure_dispatchesNewJob() throws Exception {
        WorkflowStepRequest only = step("only", List.of());
        only.setMaxRetries(0);
        Long workflowId = createWorkflow(only);
        Long executionId = executionService.createExecution(workflowId).getId();

        workflowScheduler.processExecution(executionId);

        StepExecution se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        assertThat(se.getStatus()).isEqualTo(StepExecutionStatus.RUNNING);

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
        assertThat(failed.getEvents().stream().filter(e -> e.getEventType() == ExecutionEventType.JOB_CREATED))
                .hasSize(1);

        mockMvc.perform(post("/step-executions/" + se.getId() + "/retry"))
                .andExpect(status().isOk());

        workflowScheduler.processExecution(executionId);

        ExecutionResponse after = executionService.getExecution(executionId);
        assertThat(after.getEvents().stream().filter(e -> e.getEventType() == ExecutionEventType.JOB_CREATED))
                .hasSize(2);

        verify(kubernetesBatchV1Api, times(2)).createNamespacedJob(anyString(), any(V1Job.class));
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("manual-retry-k8s-" + System.nanoTime());
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
