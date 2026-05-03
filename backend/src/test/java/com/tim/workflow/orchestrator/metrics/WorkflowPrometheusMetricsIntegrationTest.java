package com.tim.workflow.orchestrator.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.workflow.orchestrator.api.internal.StepCallbackController;
import com.tim.workflow.orchestrator.domain.StepExecutionStatus;
import com.tim.workflow.orchestrator.dto.CreateWorkflowRequest;
import com.tim.workflow.orchestrator.dto.StepResultRequest;
import com.tim.workflow.orchestrator.dto.StepResultRequest.StepResultStatus;
import com.tim.workflow.orchestrator.dto.WorkflowStepRequest;
import com.tim.workflow.orchestrator.repository.StepExecutionRepository;
import com.tim.workflow.orchestrator.scheduler.WorkflowScheduler;
import com.tim.workflow.orchestrator.service.ExecutionService;
import com.tim.workflow.orchestrator.service.WorkflowService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowPrometheusMetricsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private WorkflowScheduler workflowScheduler;

    @Autowired
    private StepExecutionRepository stepExecutionRepository;

    @Test
    void actuator_exposesPrometheusHealthAndMetrics() {
        assertThat(restTemplate.getForEntity("/actuator/prometheus", String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.getForEntity("/actuator/metrics", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void prometheusBody_containsRegisteredWorkflowMetricPrefixes() {
        WorkflowStepRequest step = step("m-only", List.of());
        Long wf = createWorkflow(step);
        executionService.createExecution(wf);

        ResponseEntity<String> res = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).contains("workflow_executions_total")
                .contains("Workflow executions persisted after successful creation");
    }

    @Test
    void executionCreated_incrementsCreatedCounter() {
        double before = counterTotal("workflow_executions_created");
        WorkflowStepRequest step = step("only", List.of());
        Long wf = createWorkflow(step);
        executionService.createExecution(wf);
        assertThat(counterTotal("workflow_executions_created")).isEqualTo(before + 1.0);
    }

    @Test
    void successfulLocalExecution_incrementsSucceededCounters() {
        WorkflowStepRequest step = step("only", List.of());
        Long wf = createWorkflow(step);
        Long executionId = executionService.createExecution(wf).getId();

        double succExecBefore = counterTotal("workflow_executions_succeeded");
        double succStepBefore = counterTotal("workflow_steps_succeeded");

        workflowScheduler.processExecution(executionId);

        assertThat(counterTotal("workflow_executions_succeeded")).isEqualTo(succExecBefore + 1.0);
        assertThat(counterTotal("workflow_steps_succeeded")).isEqualTo(succStepBefore + 1.0);
    }

    @Test
    void failedStepCallback_incrementsFailedStepCounter() throws Exception {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(0);
        Long wf = createWorkflow(step);
        Long executionId = executionService.createExecution(wf).getId();
        var se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING);
        stepExecutionRepository.save(se);

        double before = counterTotal("workflow_steps_failed");

        StepResultRequest failBody = new StepResultRequest();
        failBody.setExecutionId(executionId);
        failBody.setStepExecutionId(se.getId());
        failBody.setStatus(StepResultStatus.FAILED);
        failBody.setMessage("boom");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token");
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(failBody), headers);

        ResponseEntity<String> response =
                restTemplate.exchange("/internal/step-results", HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(counterTotal("workflow_steps_failed")).isEqualTo(before + 1.0);
    }

    @Test
    void manualRetry_incrementsManualRetryCounter() throws Exception {
        WorkflowStepRequest step = step("only", List.of());
        step.setMaxRetries(0);
        Long wf = createWorkflow(step);
        Long executionId = executionService.createExecution(wf).getId();
        var se = stepExecutionRepository.findByWorkflowExecutionIdOrderByStepIndexAsc(executionId).get(0);
        se.setStatus(StepExecutionStatus.RUNNING);
        stepExecutionRepository.save(se);

        StepResultRequest failBody = new StepResultRequest();
        failBody.setExecutionId(executionId);
        failBody.setStepExecutionId(se.getId());
        failBody.setStatus(StepResultStatus.FAILED);
        failBody.setMessage("bad");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(StepCallbackController.CALLBACK_TOKEN_HEADER, "test-callback-token");
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(failBody), headers);

        restTemplate.exchange("/internal/step-results", HttpMethod.POST, entity, String.class);

        double before = counterTotal("workflow_manual_retries_requested");
        executionService.manualRetryFailedStep(se.getId());
        assertThat(counterTotal("workflow_manual_retries_requested")).isEqualTo(before + 1.0);
    }

    @Test
    void cancelExecution_incrementsCancelledCounter() {
        WorkflowStepRequest step = step("only", List.of());
        Long wf = createWorkflow(step);
        Long executionId = executionService.createExecution(wf).getId();

        double before = counterTotal("workflow_executions_cancelled");
        executionService.cancelExecution(executionId);
        assertThat(counterTotal("workflow_executions_cancelled")).isEqualTo(before + 1.0);
    }

    private double counterTotal(String name) {
        Counter c = meterRegistry.find(name).counter();
        return c != null ? c.count() : 0.0;
    }

    private Long createWorkflow(WorkflowStepRequest... steps) {
        CreateWorkflowRequest req = new CreateWorkflowRequest();
        req.setName("metrics-test-" + System.nanoTime());
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
