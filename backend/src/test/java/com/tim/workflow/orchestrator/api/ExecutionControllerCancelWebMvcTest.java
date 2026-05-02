package com.tim.workflow.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tim.workflow.orchestrator.domain.WorkflowExecutionStatus;
import com.tim.workflow.orchestrator.dto.ExecutionResponse;
import com.tim.workflow.orchestrator.service.ExecutionService;

@WebMvcTest(controllers = ExecutionController.class)
@ExtendWith(OutputCaptureExtension.class)
class ExecutionControllerCancelWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutionService executionService;

    @Test
    void cancel_whenServiceThrows_logsErrorAndReturns500WithMessage(CapturedOutput output) throws Exception {
        when(executionService.cancelExecution(eq(42L)))
                .thenThrow(new IllegalStateException("simulated kube failure"));

        mockMvc.perform(post("/executions/42/cancel").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Cancel failed: simulated kube failure"));

        assertThat(output.getOut()).contains("Cancel failed");
        assertThat(output.getOut()).contains("IllegalStateException: simulated kube failure");
    }

    @Test
    void cancel_whenOk_returns200JsonBody() throws Exception {
        Instant now = Instant.parse("2026-05-02T12:00:00Z");
        ExecutionResponse body = new ExecutionResponse(
                1L,
                10L,
                100L,
                WorkflowExecutionStatus.CANCELLED,
                now,
                now,
                now,
                null,
                false,
                true,
                now,
                List.of(),
                List.of());
        when(executionService.cancelExecution(eq(7L))).thenReturn(body);

        mockMvc.perform(post("/executions/7/cancel").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
