package com.careerai.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/** CORS_ALLOWED_ORIGINS: exact origins only; trailing slashes tolerated; everything else refused. */
@SpringBootTest(properties = "careerai.cors.allowed-origins= https://careerai.example.com/ ,http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationTest {

    @Autowired
    private MockMvc mvc;

    private MvcResult preflight(String origin) throws Exception {
        return mvc.perform(options("/api/auth/login")
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type,authorization")).andReturn();
    }

    @Test
    void configuredOriginsArePermitted() throws Exception {
        for (String origin : List.of("https://careerai.example.com", "http://localhost:5173")) {
            MvcResult result = preflight(origin);
            assertThat(result.getResponse().getStatus()).as(origin).isEqualTo(200);
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isNull();
        }
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        for (String origin : List.of("https://evil.example", "http://careerai.example.com", "https://careerai.example.com.evil.example",
            "http://localhost:5174", "null")) {
            MvcResult result = preflight(origin);
            assertThat(result.getResponse().getStatus()).as(origin).isEqualTo(403);
            assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).as(origin).isNull();
        }
        MvcResult actual = mvc.perform(get("/api/billing/plans").header("Origin", "https://evil.example")).andReturn();
        assertThat(actual.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void originListIsNormalisedAndValidated() {
        assertThat(CorsOrigins.parse(List.of(" https://App.Example.com/ ", "http://localhost:5173", "https://app.example.com")))
            .containsExactly("https://app.example.com", "http://localhost:5173");
        for (String bad : List.of("*", "https://*.example.com", "null", "ftp://example.com", "example.com",
            "https://example.com/app", "https://example.com?x=1", "https://user@example.com")) {
            assertThatThrownBy(() -> CorsOrigins.parse(List.of(bad))).as(bad).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> CorsOrigins.parse(List.of(" ", ""))).isInstanceOf(IllegalStateException.class);
    }
}
