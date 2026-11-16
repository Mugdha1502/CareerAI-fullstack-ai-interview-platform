package com.careerai.backend.practice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** CODE_EXECUTION_PROVIDER=jdoodle without JDOODLE_CLIENT_ID/SECRET: the app starts, execution answers 503. */
@SpringBootTest(properties = { "app.execution.provider=jdoodle", "app.execution.jdoodle.client-id=", "app.execution.jdoodle.client-secret=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JDoodleNotConfiguredIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Test
    void applicationStartsAndExecutionIsUnavailable() throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "No Creds", "email", "nocreds@example.test", "password", "secret123", "confirmPassword", "secret123")))).andReturn();
        String token = JSON.readTree(registered.getResponse().getContentAsString()).path("token").asString();

        MvcResult run = mvc.perform(post("/api/practice/coding/execute").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("language", "PYTHON", "sourceCode", "print(1)", "stdin", "")))).andReturn();
        assertThat(run.getResponse().getStatus()).isEqualTo(503);
        JsonNode body = JSON.readTree(run.getResponse().getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("EXECUTION_PROVIDER_UNAVAILABLE");
        assertThat(body.path("message").asString()).isEqualTo("Code execution is temporarily unavailable.");

        MvcResult submit = mvc.perform(post("/api/practice/coding/submit").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("questionId", "coding-dp-climbing-stairs", "language", "PYTHON", "sourceCode", "print(1)")))).andReturn();
        assertThat(submit.getResponse().getStatus()).isEqualTo(503);

        MvcResult status = mvc.perform(get("/api/practice/coding/status").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(JSON.readTree(status.getResponse().getContentAsString()).path("executionAvailable").asBoolean()).isFalse();
        MvcResult history = mvc.perform(get("/api/practice/history").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(JSON.readTree(history.getResponse().getContentAsString()).path("totalItems").asInt()).isZero();
    }
}
