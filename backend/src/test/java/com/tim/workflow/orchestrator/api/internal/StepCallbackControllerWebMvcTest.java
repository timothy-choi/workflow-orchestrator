package com.tim.workflow.orchestrator.api.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tim.workflow.orchestrator.service.StepCallbackOutcome;
import com.tim.workflow.orchestrator.service.StepCallbackService;

@WebMvcTest(controllers = StepCallbackController.class)
class StepCallbackControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StepCallbackService stepCallbackService;

    @Test
    void postsStepResult_invokesServiceWithToken() throws Exception {
        when(stepCallbackService.handleStepResult(any(), any())).thenReturn(StepCallbackOutcome.ACCEPTED);

        mockMvc.perform(post("/internal/step-results")
                        .header(StepCallbackController.CALLBACK_TOKEN_HEADER, "my-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "executionId": 10,
                                  "stepExecutionId": 20,
                                  "status": "SUCCESS",
                                  "message": "done"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(stepCallbackService).handleStepResult(any(), eq("my-token"));
    }
}
