package com.talent.server.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base class for API and persistence integration tests: full application context, real PostgreSQL,
 * MockMvc, and a clean database before every test. Tests run without a surrounding transaction so
 * that commits, locks and concurrency behave exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestData.class)
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected TestData data;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                TRUNCATE TABLE ai_tool_calls, ai_agent_runs, training_recommendations, learning_resources,
                               allocation_events, allocations, demand_skills, project_demands, employee_skills,
                               skills, employees
                RESTART IDENTITY CASCADE
                """);
    }

    protected static RequestPostProcessor asManager() {
        return as("manager", "RESOURCE_MANAGER");
    }

    protected static RequestPostProcessor asApprover() {
        return as("approver", "APPROVER");
    }

    protected static RequestPostProcessor as(String username, String role) {
        return jwt().jwt(j -> j.subject(username).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    protected String toJson(Object value) {
        return json.writeValueAsString(value);
    }
}
