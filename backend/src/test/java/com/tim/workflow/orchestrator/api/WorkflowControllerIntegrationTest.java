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
class WorkflowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListAndGetWorkflow_roundTrip() throws Exception {
        ObjectNode step = objectMapper.createObjectNode();
        step.put("name", "step-a");
        step.put("image", "busybox:latest");
        step.put("command", "echo ok");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", "integration-test-workflow");
        body.put("description", "desc");
        ArrayNode steps = objectMapper.createArrayNode();
        steps.add(step);
        body.set("steps", steps);

        String createResponse = mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("integration-test-workflow"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(createResponse).get("id").asLong();

        String listJson = mockMvc.perform(get("/workflows"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode list = objectMapper.readTree(listJson);
        assertThat(list.isArray()).isTrue();
        boolean found = false;
        for (JsonNode row : list) {
            if (row.get("id").asLong() == id) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();

        mockMvc.perform(get("/workflows/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionJson").isString())
                .andExpect(jsonPath("$.currentVersion").value(1));
    }

    @Test
    void createWorkflow_missingName_returnsBadRequest() throws Exception {
        ObjectNode body = validWorkflowBody();
        body.remove("name");

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflow_missingSteps_returnsBadRequest() throws Exception {
        ObjectNode body = validWorkflowBody();
        body.remove("steps");

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflow_emptySteps_returnsBadRequest() throws Exception {
        ObjectNode body = validWorkflowBody();
        body.set("steps", objectMapper.createArrayNode());

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWorkflow_stepMissingRequiredFields_returnsBadRequest() throws Exception {
        ObjectNode body = validWorkflowBody();
        ObjectNode incompleteStep = objectMapper.createObjectNode();
        incompleteStep.put("name", "only-name");
        ArrayNode steps = objectMapper.createArrayNode();
        steps.add(incompleteStep);
        body.set("steps", steps);

        mockMvc.perform(post("/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private ObjectNode validWorkflowBody() {
        ObjectNode step = objectMapper.createObjectNode();
        step.put("name", "step-a");
        step.put("image", "busybox:latest");
        step.put("command", "echo ok");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", "valid-body-workflow");
        ArrayNode steps = objectMapper.createArrayNode();
        steps.add(step);
        body.set("steps", steps);
        return body;
    }
}
