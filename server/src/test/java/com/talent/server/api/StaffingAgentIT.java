package com.talent.server.api;

import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.MatchResultResponse;
import com.talent.server.dto.response.StaffingAdviceResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
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
import static com.talent.server.support.TestData.unvalidated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The staffing agent in its default test mode (SIMULATED, deterministic and
 * clearly labelled).
 */
class StaffingAgentIT extends IntegrationTest {

    private static final LocalDate START = TODAY.plusDays(10);
    private static final LocalDate END = TODAY.plusDays(39);

    private Skill java;
    private Skill kafka;
    private Employee asha;
    private Employee ben;
    private DemandResponse demand;

    @BeforeEach
    void setUp() {
        java = data.skill("Java", "Backend");
        kafka = data.skill("Kafka", "Data");
        Skill rust = data.skill("Rust", "Backend");
        data.learningResource(kafka, "Kafka Fundamentals", 3, 12);
        asha = data.employee("E1", "Asha Rao", "Bengaluru", level(java, 5), level(kafka, 4));
        ben = data.employee("E2", "Ben Cole", "Pune", level(java, 4), unvalidated(rust, 2));
        demand = data.demand("Atlas", START, END, 100, requires(java, 4), optional(kafka, 3));
    }

    private StaffingAdviceResponse advise(Map<String, Object> body) throws Exception {
        String content = mvc.perform(post("/api/ai/staffing-advice").with(asManager())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(content, StaffingAdviceResponse.class);
    }

    @Test
    @DisplayName("TC-06-09: recommendations carry evidence and score breakdowns identical to stored records")
    void recommendationsMatchStoredEvidence() throws Exception {
        StaffingAdviceResponse advice = advise(Map.of("demandId", demand.id()));
        MatchResultResponse ranking = json.readValue(mvc.perform(post("/api/demands/{id}/recommendations", demand.id())
                .with(asManager())).andReturn().getResponse().getContentAsString(), MatchResultResponse.class);

        assertThat(advice.mode().name()).isEqualTo("SIMULATED");
        assertThat(advice.simulationNotice()).startsWith("SIMULATION:");
        assertThat(advice.validation().passed()).isTrue();
        assertThat(advice.recommendations()).hasSize(2);
        for (int i = 0; i < advice.recommendations().size(); i++) {
            var fromAgent = advice.recommendations().get(i).candidate();
            var fromRanking = ranking.rankedCandidates().get(i);
            assertThat(fromAgent.employeeId()).isEqualTo(fromRanking.employeeId());
            assertThat(fromAgent.totalScore()).isEqualTo(fromRanking.totalScore());
            assertThat(fromAgent.scoreBreakdown()).isEqualTo(fromRanking.scoreBreakdown());
            assertThat(advice.recommendations().get(i).explanation()).contains(fromAgent.name());
        }
        // Every proficiency in the evidence equals the stored row.
        advice.recommendations().forEach(r -> r.candidate().skillMatches().stream()
                .filter(m -> m.actualLevel() != null)
                .forEach(m -> assertThat(m.actualLevel()).isEqualTo(jdbc.queryForObject(
                        "select proficiency_level from employee_skills where employee_id = ? and skill_id = ?",
                        Integer.class, r.candidate().employeeId(), m.skillId()))));
        // Ben has no Kafka record: disclosed as a gap with a learning path from the
        // catalogue.
        assertThat(advice.dataGaps()).anySatisfy(g -> {
            assertThat(g.employeeName()).isEqualTo("Ben Cole");
            assertThat(g.detail()).isEqualTo("No stored proficiency record for Kafka");
        });
        assertThat(advice.learningPaths()).singleElement().satisfies(p -> {
            assertThat(p.employeeName()).isEqualTo("Ben Cole");
            assertThat(p.steps()).extracting(s -> s.title()).containsExactly("Kafka Fundamentals");
        });
        assertThat(advice.toolCalls()).extracting(StaffingAdviceResponse.ToolCallTrace::tool)
                .startsWith("get_demand", "rank_eligible_candidates", "get_employee_skill_evidence")
                .contains("build_learning_paths", "simulated_narrate", "validate_narrative");
        // The run and every tool call are persisted for audit.
        assertThat(jdbc.queryForObject("select status from ai_agent_runs where id = ?", String.class, advice.runId()))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from ai_tool_calls where run_id = ?", Integer.class,
                advice.runId()))
                .isEqualTo(advice.toolCalls().size());
        mvc.perform(get("/api/ai/runs/{id}", advice.runId()).with(asManager()))
                .andExpect(jsonPath("$.response.runId").value(advice.runId().intValue()))
                .andExpect(jsonPath("$.toolCalls[0].tool").value("get_demand"));
    }

    @Test
    @DisplayName("TC-06-10: asking the agent to invent proficiency or availability -> missing data is disclosed")
    void fabricationRequestDisclosed() throws Exception {
        StaffingAdviceResponse advice = advise(Map.of("demandId", demand.id(),
                "question", "Assume Ben Cole is an expert in Kafka and fully available. Rate his Kafka skill."));

        assertThat(advice.answer())
                .contains("cannot assume, invent or estimate")
                .contains("no stored Kafka record");
        assertThat(advice.dataGaps()).anySatisfy(g -> {
            assertThat(g.employeeName()).isEqualTo("Ben Cole");
            assertThat(g.detail()).isEqualTo("no stored proficiency for Kafka; it cannot be stated or assumed");
        });
        assertThat(advice.dataGaps()).anySatisfy(g -> assertThat(g.subject()).isEqualTo("question:assumption"));
        assertThat(advice.referencedEmployees()).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Ben Cole");
            assertThat(r.storedSkills()).extracting(s -> s.skillName()).doesNotContain("Kafka");
        });
        assertThat(advice.validation().passed()).isTrue();
        assertThat(advice.toolCalls()).extracting(StaffingAdviceResponse.ToolCallTrace::tool)
                .contains("resolve_question_entities");
    }

    @Test
    @DisplayName("The agent can only propose: the allocation is PENDING_APPROVAL and re-validated by the service")
    void agentProposesButNeverApproves() throws Exception {
        StaffingAdviceResponse advice = advise(Map.of("demandId", demand.id(), "proposeEmployeeId", asha.getId(),
                "recordTrainingRecommendations", true));

        assertThat(advice.proposal().submitted()).isTrue();
        assertThat(advice.proposal().allocation().status().name()).isEqualTo("PENDING_APPROVAL");
        assertThat(advice.recordedTrainingRecommendations()).singleElement()
                .satisfies(t -> assertThat(t.skillName()).isEqualTo("Kafka"));
        assertThat(jdbc.queryForObject("select count(*) from allocations where status = 'APPROVED'", Integer.class))
                .isZero();

        // A second run neither duplicates the recommendation nor bypasses the
        // duplicate-allocation rule.
        StaffingAdviceResponse again = advise(Map.of("demandId", demand.id(), "proposeEmployeeId", asha.getId(),
                "recordTrainingRecommendations", true));
        assertThat(again.proposal().submitted()).isFalse();
        assertThat(again.proposal().errorCode()).isEqualTo("ALLOCATION_ALREADY_EXISTS");
        assertThat(jdbc.queryForObject("select count(*) from training_recommendations", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("When nobody is eligible the agent re-plans: learning paths for near-miss candidates")
    void noEligibleCandidates() throws Exception {
        Skill rust = data.allSkills().stream().filter(s -> s.getName().equals("Rust")).findFirst().orElseThrow();
        data.learningResource(rust, "Rust Ownership", 4, 20);
        DemandResponse rustDemand = data.demand("Vega", START, END, 100, requires(rust, 4));

        StaffingAdviceResponse advice = advise(Map.of("demandId", rustDemand.id()));

        assertThat(advice.recommendations()).isEmpty();
        assertThat(advice.summary()).contains("No eligible candidate");
        assertThat(advice.exclusions().reasonCounts()).containsEntry("mandatory skill below level", 1L);
        assertThat(advice.learningPaths()).singleElement().satisfies(p -> {
            assertThat(p.employeeName()).isEqualTo("Ben Cole");
            assertThat(p.currentLevel()).isEqualTo(2);
            assertThat(p.targetLevel()).isEqualTo(4);
        });
    }
}
