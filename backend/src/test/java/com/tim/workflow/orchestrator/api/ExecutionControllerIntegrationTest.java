package com.tim.workflow.orchestrator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExecutionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAndGetExecution_roundTrip() throws Exception {
        long workflowId = createWorkflow("exec-round-trip-wf");

        String createExecJson = mockMvc.perform(post("/executions").param("workflowId", String.valueOf(workflowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.workflowId").value(workflowId))
                .andExpect(jsonPath("$.steps").isArray())
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].status").value("PENDING"))
                .andExpect(jsonPath("$.steps[1].stepIndex").value(1))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events[0].eventType").value("EXECUTION_CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long executionId = objectMapper.readTree(createExecJson).get("id").asLong();

        mockMvc.perform(get("/executions/" + executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(executionId))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.events[0].payload").isString());
    }

    @Test
    void createExecution_unknownWorkflow_returnsNotFound() throws Exception {
        mockMvc.perform(post("/executions").param("workflowId", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExecution_unknown_returnsNotFound() throws Exception {
        mockMvc.perform(get("/executions/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void listExecutionEvents_returnsEventsOrderedByCreatedAt() throws Exception {
        long workflowId = createWorkflow("exec-events-api-wf");

        String createExecJson = mockMvc.perform(post("/executions").param("workflowId", String.valueOf(workflowId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long executionId = objectMapper.readTree(createExecJson).get("id").asLong();

        mockMvc.perform(get("/executions/" + executionId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("EXECUTION_CREATED"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void listExecutionEvents_unknownExecution_returnsNotFound() throws Exception {
        mockMvc.perform(get("/executions/999999999/events")).andExpect(status().isNotFound());
    }

    private long createWorkflow(String name) throws Exception {
        ObjectNode stepA = objectMapper.createObjectNode();
        stepA.put("name", "step-a");
        stepA.put("image", "busybox:latest");
        stepA.put("command", "echo a");

        ObjectNode stepB = objectMapper.createObjectNode();
        stepB.put("name", "step-b");
        stepB.put("image", "busybox:latest");
        stepB.put("command", "echo b");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        ArrayNode steps = objectMapper.createArrayNode();
        steps.add(stepA);
        steps.add(stepB);
        body.set("steps", steps);

        String response = mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode idNode = objectMapper.readTree(response).get("id");
        assertThat(idNode).isNotNull();
        return idNode.asLong();
    }
}
