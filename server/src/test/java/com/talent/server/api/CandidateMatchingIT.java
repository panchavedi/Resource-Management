package com.talent.server.api;

import com.talent.server.dto.response.DemandResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
import com.talent.server.enums.EmploymentStatus;
import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static com.talent.server.support.TestData.TODAY;
import static com.talent.server.support.TestData.level;
import static com.talent.server.support.TestData.optional;
import static com.talent.server.support.TestData.requires;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eligibility, ranking and availability through the REST API against
 * PostgreSQL.
 */
class CandidateMatchingIT extends IntegrationTest {

        private static final LocalDate START = TODAY.plusDays(10);
        private static final LocalDate END = TODAY.plusDays(39); // 30 days

        private Skill java;
        private Skill sql;
        private Skill kafka;
        private DemandResponse demand;

        @BeforeEach
        void setUp() {
                java = data.skill("Java", "Backend");
                sql = data.skill("SQL", "Data");
                kafka = data.skill("Kafka", "Data");
                demand = data.demand("Atlas", START, END, 100, requires(java, 4), requires(sql, 3), optional(kafka, 3));
        }

        @Test
        @DisplayName("TC-06-03: ranking follows the documented criteria and exposes the score breakdown")
        void rankingFollowsCriteria() throws Exception {
                data.employee("E1", "Asha Rao", "Bengaluru", level(java, 5), level(sql, 4), level(kafka, 4));
                data.employee("E2", "Ben Cole", "Pune", level(java, 4), level(sql, 3));
                data.employee("E3", "Chen Wu", "Bengaluru", level(java, 4), level(sql, 3));

                mvc.perform(post("/api/demands/{id}/recommendations", demand.id()).with(asManager()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.criteria.mandatorySkillPolicy").value("EXCLUDE"))
                                .andExpect(jsonPath("$.criteria.rules", hasSize(6)))
                                .andExpect(jsonPath("$.rankedCandidates[*].name",
                                                contains("Asha Rao", "Chen Wu", "Ben Cole")))
                                .andExpect(jsonPath("$.rankedCandidates[*].rank", contains(1, 2, 3)))
                                .andExpect(jsonPath("$.rankedCandidates[0].scoreBreakdown[*].factor",
                                                contains("SKILL_FIT", "AVAILABILITY", "LOCATION", "EVIDENCE_QUALITY")))
                                .andExpect(jsonPath("$.rankedCandidates[0].scoreBreakdown[0].weight").value(0.5))
                                // Chen Wu outranks Ben Cole only through location (same city, demand is in
                                // Bengaluru)
                                .andExpect(jsonPath("$.rankedCandidates[1].locationFit.fit").value("SAME_LOCATION"))
                                .andExpect(jsonPath("$.rankedCandidates[2].locationFit.fit").value("REMOTE"))
                                .andExpect(jsonPath("$.rankedCandidates[2].gaps[0].skillName").value("Kafka"));
        }

        @Test
        @DisplayName("TC-06-04: a candidate lacking a mandatory skill is excluded with the reason")
        void missingMandatorySkillExcluded() throws Exception {
                data.employee("E1", "Asha Rao", "Bengaluru", level(java, 5), level(sql, 4));
                data.employee("E2", "Ben Cole", "Bengaluru", level(java, 5));
                data.employee("E3", "Chen Wu", "Bengaluru", level(java, 3), level(sql, 5));

                mvc.perform(post("/api/demands/{id}/recommendations", demand.id()).with(asManager()))
                                .andExpect(jsonPath("$.rankedCandidates[*].name", contains("Asha Rao")))
                                .andExpect(jsonPath("$.excludedCandidates", hasSize(2)))
                                .andExpect(jsonPath("$.excludedCandidates[?(@.name=='Ben Cole')].exclusionReasons[0]")
                                                .value("Missing mandatory skill SQL (requires level 3, no stored record)"))
                                .andExpect(jsonPath("$.excludedCandidates[?(@.name=='Chen Wu')].exclusionReasons[0]")
                                                .value("Mandatory skill Java below required level (stored 3, requires 4)"));
        }

        @Test
        @DisplayName("TC-06-05: a candidate allocated for the entire period is unavailable and cannot be allocated")
        void fullyAllocatedCandidateUnavailable() throws Exception {
                Employee busy = data.employee("E1", "Asha Rao", "Bengaluru", level(java, 5), level(sql, 4));
                DemandResponse other = data.demand("Other", "Engineer", "Bengaluru", TODAY.plusDays(1),
                                TODAY.plusDays(60), 100, 1,
                                requires(java, 3));
                data.approved(busy.getId(), other.id(), TODAY.plusDays(1), TODAY.plusDays(60), 100);

                mvc.perform(post("/api/demands/{id}/recommendations", demand.id()).with(asManager()))
                                .andExpect(jsonPath("$.rankedCandidates", hasSize(0)))
                                .andExpect(jsonPath("$.excludedCandidates[0].availability.status").value("NONE"))
                                .andExpect(jsonPath("$.excludedCandidates[0].availability.availableDays").value(0))
                                .andExpect(jsonPath("$.excludedCandidates[0].exclusionReasons[0]",
                                                startsWith("Not available")));

                mvc.perform(post("/api/allocations").with(asManager()).contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(Map.of("employeeId", busy.getId(), "demandId", demand.id()))))
                                .andExpect(status().isUnprocessableContent())
                                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"))
                                .andExpect(jsonPath("$.details.peakLoadPercent").value(100));
        }

        @Test
        @DisplayName("TC-06-06: partial availability is reported with exact days, coverage and windows")
        void partialAvailabilityReported() throws Exception {
                Employee partly = data.employee("E1", "Asha Rao", "Bengaluru", level(java, 5), level(sql, 4));
                DemandResponse other = data.demand("Other", "Engineer", "Bengaluru", TODAY.plusDays(1),
                                START.plusDays(9), 100, 1,
                                requires(java, 3));
                data.approved(partly.getId(), other.id(), TODAY.plusDays(1), START.plusDays(9), 100); // busy for 10 of
                                                                                                      // 30 days

                mvc.perform(post("/api/demands/{id}/recommendations", demand.id()).with(asManager()))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.status").value("PARTIAL"))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.totalDays").value(30))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.availableDays").value(20))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.coveragePercent").value(66.7))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.windows", hasSize(2)))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.windows[0].freePercent")
                                                .value(0))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.windows[1].startDate")
                                                .value(START.plusDays(10).toString()))
                                .andExpect(jsonPath("$.rankedCandidates[0].flags",
                                                hasItem(startsWith("Partially available: 20 of 30 days"))));

                mvc.perform(get("/api/employees/{id}/availability", partly.getId()).with(asManager())
                                .param("startDate", START.toString()).param("endDate", END.toString())
                                .param("allocationPercent", "100"))
                                .andExpect(jsonPath("$.status").value("PARTIAL"))
                                .andExpect(jsonPath("$.availableDays").value(20));
        }

        @Test
        @DisplayName("Employees on leave are excluded; part-time capacity is respected by resource search")
        void statusAndCapacity() throws Exception {
                data.employee("E1", "Asha Rao", "Bengaluru", EmploymentStatus.ON_LEAVE, 100, level(java, 5),
                                level(sql, 4));
                data.employee("E2", "Ben Cole", "Bengaluru", EmploymentStatus.ACTIVE, 50, level(java, 5),
                                level(sql, 4));

                mvc.perform(post("/api/demands/{id}/recommendations", demand.id()).with(asManager()))
                                .andExpect(jsonPath("$.excludedCandidates[0].exclusionReasons[0]")
                                                .value("Employment status ON_LEAVE is not assignable"))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.status").value("PARTIAL"))
                                .andExpect(jsonPath("$.rankedCandidates[0].availability.minFreePercent").value(50));

                mvc.perform(get("/api/resources/available").with(asManager())
                                .param("startDate", START.toString()).param("endDate", END.toString())
                                .param("skillIds", java.getId().toString()).param("minProficiency", "4")
                                .param("allocationPercent", "50"))
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].name").value("Ben Cole"))
                                .andExpect(jsonPath("$[0].availability.status").value("FULL"));
        }
}
