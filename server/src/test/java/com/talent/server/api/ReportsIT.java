package com.talent.server.api;

import com.talent.server.dto.response.DemandResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.talent.server.support.TestData.TODAY;
import static com.talent.server.support.TestData.level;
import static com.talent.server.support.TestData.requires;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The six SQL tasks, executed against PostgreSQL through the reporting API. */
class ReportsIT extends IntegrationTest {

    private static final LocalDate START = TODAY.plusDays(1);
    private static final LocalDate END = TODAY.plusDays(10);

    private Employee asha;
    private DemandResponse staffed;

    @BeforeEach
    void setUp() {
        Skill java = data.skill("Java", "Backend");
        Skill rust = data.skill("Rust", "Systems");
        asha = data.employee("E1", "Asha Rao", "Pune", level(java, 5));
        data.employee("E2", "Ben Cole", "Pune", level(java, 3), level(rust, 2));
        staffed = data.demand("Atlas", "Java Engineer", "Pune", START, END, 50, 2, requires(java, 4));
        data.demand("Vega", "Rust Engineer", "Pune", START, END, 100, 1, requires(rust, 4));
        data.approved(asha.getId(), staffed.id(), START, END, 50);
    }

    @Test
    @DisplayName("SQL 1: open demands by role and priority with open positions")
    void openDemands() throws Exception {
        mvc.perform(get("/api/reports/open-demands").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.role=='Java Engineer')].openPositions").value(1))
                .andExpect(jsonPath("$[?(@.role=='Java Engineer')].approvedCount").value(1));
    }

    @Test
    @DisplayName("SQL 2: resources grouped by skill and proficiency")
    void skillProficiency() throws Exception {
        mvc.perform(get("/api/reports/skill-proficiency").with(asManager()))
                .andExpect(jsonPath("$[*].skill", contains("Java", "Java", "Rust")))
                .andExpect(jsonPath("$[0].proficiencyLevel").value(5))
                .andExpect(jsonPath("$[0].employees").value("Asha Rao"));
    }

    @Test
    @DisplayName("SQL 3: available resources for a date range respect per-day load")
    void availableResources() throws Exception {
        mvc.perform(get("/api/reports/available-resources").with(asManager())
                .param("from", START.toString()).param("to", END.toString()).param("allocationPercent", "60"))
                .andExpect(jsonPath("$[*].name", contains("Ben Cole")));
        mvc.perform(get("/api/reports/available-resources").with(asManager())
                .param("from", START.toString()).param("to", END.toString()).param("allocationPercent", "50"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name=='Asha Rao')].peakLoadPercent").value(50));
    }

    @Test
    @DisplayName("SQL 4: utilisation by employee and by department")
    void utilization() throws Exception {
        mvc.perform(get("/api/reports/utilization").with(asManager())
                .param("from", START.toString()).param("to", END.toString()))
                .andExpect(jsonPath("$.byEmployee[?(@.name=='Asha Rao')].utilizationPercent").value(50.0))
                .andExpect(jsonPath("$.byEmployee[?(@.name=='Ben Cole')].utilizationPercent").value(0.0))
                .andExpect(jsonPath("$.byDepartment[0].department").value("Engineering"))
                .andExpect(jsonPath("$.byDepartment[0].utilizationPercent").value(25.0));
    }

    @Test
    @DisplayName("SQL 5: demands with no eligible candidate")
    void unstaffableDemands() throws Exception {
        mvc.perform(get("/api/reports/unstaffable-demands").with(asManager()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectName").value("Vega"))
                .andExpect(jsonPath("$[0].skillQualifiedCount").value(0));
    }

    @Test
    @DisplayName("SQL 6: skill gaps grouped by category, with per-skill detail")
    void skillGaps() throws Exception {
        mvc.perform(get("/api/reports/skill-gaps").with(asManager()))
                .andExpect(jsonPath("$.byCategory", hasSize(2)))
                .andExpect(jsonPath("$.byCategory[?(@.category=='Systems')].peopleShort").value(1))
                .andExpect(jsonPath("$.byCategory[?(@.category=='Systems')].upskillCandidates").value(1))
                .andExpect(jsonPath("$.byCategory[?(@.category=='Backend')].skillsShort").value("Java"))
                .andExpect(jsonPath("$.bySkill[?(@.skill=='Rust')].nearMissSupply").value(1));
    }

    @Test
    @DisplayName("Dashboard KPIs and range validation")
    void dashboardAndValidation() throws Exception {
        mvc.perform(get("/api/reports/dashboard").with(asManager()))
                .andExpect(jsonPath("$.openDemands").value(2))
                .andExpect(jsonPath("$.openPositions").value(2))
                .andExpect(jsonPath("$.unstaffableDemands").value(1));
        mvc.perform(get("/api/reports/utilization").with(asManager())
                .param("from", END.toString()).param("to", START.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }
}
