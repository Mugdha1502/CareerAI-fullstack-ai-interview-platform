package com.careerai.backend.exception;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Client mistakes get a JSON 4xx — never a 500 or framework internals (in-memory test database). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorResponseIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    private String token() throws Exception {
        String email = "errors-" + System.nanoTime() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "Error Tester", "email", email, "password", "secret123", "confirmPassword", "secret123")))).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).path("token").asString();
    }

    private record Response(int status, JsonNode body, String raw) {
    }

    private Response perform(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString();
        return new Response(result.getResponse().getStatus(), raw.isBlank() ? JSON.nullNode() : JSON.readTree(raw), raw);
    }

    @Test
    void nonNumericPathAndQueryValuesAreBadRequests() throws Exception {
        String token = token();
        for (String path : new String[] { "/api/interviews/abc", "/api/resumes/abc", "/api/coach/conversations/abc", "/api/practice/history?page=abc" }) {
            Response response = perform(get(path).header("Authorization", "Bearer " + token));
            assertThat(response.status()).as(path).isEqualTo(400);
            assertThat(response.body().path("message").asString()).as(path).startsWith("Invalid value for '");
            assertThat(response.raw()).as(path).doesNotContain("Exception").doesNotContain("java.lang").doesNotContain("abc");
        }
    }

    @Test
    void frameworkErrorsKeepTheirStatusAsJson() throws Exception {
        String token = token();
        Response missing = perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token));
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body().path("message").isString()).isTrue();
        Response method = perform(delete("/api/auth/me").header("Authorization", "Bearer " + token));
        assertThat(method.status()).isEqualTo(405);
        Response malformed = perform(post("/api/coach/conversations").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{not json"));
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.raw()).doesNotContain("Exception");
    }
}
