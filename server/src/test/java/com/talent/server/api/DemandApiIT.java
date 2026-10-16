package com.talent.server.api;

import com.talent.server.entity.Skill;
import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.talent.server.support.TestData.TODAY;
import static com.talent.server.support.TestData.requires;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemandApiIT extends IntegrationTest {

    private Skill java;

    @BeforeEach
    void skills() {
        java = data.skill("Java", "Backend");
    }

    private Map<String, Object> validDemand() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectName", "Atlas Payments");
        body.put("role", "Senior Java Engineer");
        body.put("startDate", TODAY.plusDays(7).toString());
        body.put("endDate", TODAY.plusDays(90).toString());
        body.put("location", "Bengaluru");
        body.put("remoteAllowed", true);
        body.put("allocationPercent", 80);
        body.put("headcount", 2);
        body.put("priority", "CRITICAL");
        body.put("requiredSkills", List.of(Map.of("skillId", java.getId(), "minimumProficiency", 4, "mandatory", true)));
        return body;
    }

    @Test
    @DisplayName("TC-06-01: create a demand with valid fields -> 201, persisted and retrievable")
    void createValidDemand() throws Exception {
        String location = mvc.perform(post("/api/demands").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(validDemand())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/demands/")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.role").value("Senior Java Engineer"))
                .andExpect(jsonPath("$.allocationPercent").value(80))
                .andExpect(jsonPath("$.durationDays").value(84))
                .andExpect(jsonPath("$.createdBy").value("manager"))
                .andExpect(jsonPath("$.requiredSkills[0].skillName").value("Java"))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location).with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Atlas Payments"));
        assertThat(jdbc.queryForObject("select count(*) from project_demands", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from demand_skills", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-06-02: missing role and end date before start date -> 400 with field errors, nothing stored")
    void rejectMissingRoleAndInvalidDates() throws Exception {
        Map<String, Object> body = validDemand();
        body.remove("role");
        body.put("endDate", TODAY.plusDays(1).toString());

        mvc.perform(post("/api/demands").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("role", "endDate")));
        assertThat(jdbc.queryForObject("select count(*) from project_demands", Integer.class)).isZero();
    }

    @Test
    @DisplayName("TC-06-02: start date in the past, allocation over 100% and missing priority are rejected")
    void rejectOtherInvalidFields() throws Exception {
        Map<String, Object> body = validDemand();
        body.put("startDate", TODAY.minusDays(3).toString());
        body.put("allocationPercent", 120);
        body.remove("priority");

        mvc.perform(post("/api/demands").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("startDate", "allocationPercent", "priority")));
    }

    @Test
    @DisplayName("TC-06-02: malformed dates and unknown enum values return MALFORMED_REQUEST")
    void rejectMalformedBody() throws Exception {
        Map<String, Object> body = validDemand();
        body.put("startDate", "next week");

        mvc.perform(post("/api/demands").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Unknown or duplicated skills are rejected")
    void rejectBadSkills() throws Exception {
        Map<String, Object> unknown = validDemand();
        unknown.put("requiredSkills", List.of(Map.of("skillId", 999, "minimumProficiency", 3)));
        mvc.perform(post("/api/demands").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(unknown)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        Map<String, Object> duplicate = validDemand();
        duplicate.put("requiredSkills", List.of(Map.of("skillId", java.getId(), "minimumProficiency", 3),
                Map.of("skillId", java.getId(), "minimumProficiency", 4)));
        mvc.perform(post("/api/demands").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(duplicate)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Only resource managers can create demands; anonymous callers get 401")
    void authorization() throws Exception {
        mvc.perform(post("/api/demands").with(asApprover())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(validDemand())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/demands").contentType(MediaType.APPLICATION_JSON).content(toJson(validDemand())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/demands filters by status, skill and date window")
    void filters() throws Exception {
        Skill react = data.skill("React", "Frontend");
        data.demand("Backend", TODAY.plusDays(5), TODAY.plusDays(30), 100, requires(java, 3));
        data.demand("Frontend", TODAY.plusDays(60), TODAY.plusDays(90), 100, requires(react, 3));

        mvc.perform(get("/api/demands").param("skillId", java.getId().toString()).with(asManager()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].projectName").value("Backend"));
        mvc.perform(get("/api/demands").param("from", TODAY.plusDays(50).toString())
                        .param("to", TODAY.plusDays(70).toString()).with(asManager()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].projectName").value("Frontend"));
        mvc.perform(get("/api/demands").param("status", "FILLED").with(asManager()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
