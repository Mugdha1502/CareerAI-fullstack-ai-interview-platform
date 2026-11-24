package com.careerai.backend.practice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A double-clicked "Finish" sends the same result several times at once: exactly one is saved and the
 * others get 409 — never a 500 from a database deadlock (in-memory test database).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticeResultConcurrencyTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    private String json(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    @Test
    void concurrentSavesForOneSessionStoreExactlyOneResult() throws Exception {
        String email = "concurrent-" + System.nanoTime() + "@example.test";
        String token = JSON.readTree(json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "Concurrent", "email", email, "password", "secret123", "confirmPassword", "secret123")))).andReturn()))
            .path("token").asString();
        long sessionId = JSON.readTree(json(mvc.perform(post("/api/practice/sessions").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"module\":\"aptitude\"}")).andReturn())).path("id").asLong();
        String body = JSON.writeValueAsString(Map.of("sessionId", sessionId, "score", 3, "totalQuestions", 5, "correctAnswers", 3, "incorrectAnswers", 2));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            statuses.add(pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/practice/results").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> status : statuses) results.add(status.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(results).filteredOn(status -> status >= 200 && status < 300).hasSize(1);
        assertThat(results).filteredOn(status -> status == 409).hasSize(threads - 1);
        long saved = JSON.readTree(json(mvc.perform(get("/api/practice/history").header("Authorization", "Bearer " + token)).andReturn()))
            .path("totalItems").asLong();
        assertThat(saved).isEqualTo(1);
    }
}
