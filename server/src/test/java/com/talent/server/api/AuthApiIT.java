package com.talent.server.api;

import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIT extends IntegrationTest {

    @Test
    @DisplayName("Login issues a signed token that grants access with the user's roles")
    void loginAndUseToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "approver", "password", "test-approver-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.roles", contains("APPROVER")))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("accessToken").asString();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("approver"))
                .andExpect(jsonPath("$.displayName").value("Test Approver"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token + "tampered"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Wrong passwords are rejected and repeated failures lock the account")
    void badCredentialsAndLockout() throws Exception {
        String wrong = toJson(Map.of("username", "manager", "password", "nope"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "manager", "password", "test-manager-password"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TEMPORARILY_LOCKED"));
    }

    @Test
    @DisplayName("Health is public; API calls without a token get a 401 problem document")
    void publicAndProtectedEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/skills"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
