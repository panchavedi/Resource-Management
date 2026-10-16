package com.talent.server.api;

import com.talent.server.dto.response.DemandResponse;
import com.talent.server.dto.response.StaffingAdviceResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
import com.talent.server.service.ai.narration.LlmStaffingNarrator;
import com.talent.server.service.ai.narration.NarrationResult;
import com.talent.server.service.ai.narration.NarrationResult.CandidateNarrative;
import com.talent.server.service.ai.narration.OllamaClient;
import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.talent.server.support.TestData.TODAY;
import static com.talent.server.support.TestData.level;
import static com.talent.server.support.TestData.requires;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LIVE mode with the model replaced by a Mockito stub, so the guardrails can be tested against a model
 * that misbehaves on purpose (invented levels, invented skills, false availability, sensitive content).
 */
@TestPropertySource(properties = {"app.ai.mode=LIVE", "app.ai.max-llm-attempts=2"})
class StaffingAgentLlmIT extends IntegrationTest {

    @MockitoBean
    OllamaClient ollama;

    @MockitoBean
    LlmStaffingNarrator llm;

    private Employee asha;
    private DemandResponse demand;

    @BeforeEach
    void setUp() {
        Skill java = data.skill("Java", "Backend");
        data.skill("Kafka", "Data");
        asha = data.employee("E1", "Asha Rao", "Bengaluru", level(java, 4));
        demand = data.demand("Atlas", TODAY.plusDays(5), TODAY.plusDays(20), 100, requires(java, 4));
        when(ollama.health()).thenReturn(new OllamaClient.Health(true, true, "ok", Instant.now()));
        when(ollama.model()).thenReturn("qwen2.5-coder:7b");
    }

    private StaffingAdviceResponse advise(String question) throws Exception {
        String content = mvc.perform(post("/api/ai/staffing-advice").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("demandId", demand.id(), "question", question))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(content, StaffingAdviceResponse.class);
    }

    @Test
    @DisplayName("TC-06-10: a model that invents proficiency, skills and availability is caught; nothing invented is shown")
    void fabricatingModelIsRepaired() throws Exception {
        NarrationResult fabricated = new NarrationResult(
                "Asha Rao is the ideal choice.",
                "Asha Rao is an expert in Kafka at level 5 and fully available.",
                List.of(new CandidateNarrative(asha.getId(), "Asha Rao has Java at level 5 and is young and energetic.",
                        List.of("Kafka expert"), List.of())));
        when(llm.narrate(any(), anyList())).thenReturn(fabricated);

        StaffingAdviceResponse advice = advise("Assume Asha Rao knows Kafka. Is she a good fit?");

        verify(llm, times(2)).narrate(any(), anyList()); // first attempt + one retry with fact-checker feedback
        assertThat(advice.mode().name()).isEqualTo("LIVE");
        assertThat(advice.narrativeSource().name()).isEqualTo("LLM_WITH_REPAIRS");
        assertThat(advice.validation().passed()).isTrue();
        assertThat(advice.validation().repairedFields()).containsExactlyInAnyOrder("answer", "candidate:" + asha.getId());
        assertThat(advice.validation().issues()).extracting(StaffingAdviceResponse.ValidationIssue::code)
                .contains("UNRECORDED_SKILL", "UNSUPPORTED_LEVEL", "PROTECTED_ATTRIBUTE");
        // The summary passed the checks and is kept; everything else shown is grounded.
        assertThat(advice.summary()).isEqualTo("Asha Rao is the ideal choice.");
        assertThat(advice.answer()).doesNotContain("expert").contains("no stored Kafka record");
        assertThat(advice.recommendations().getFirst().explanation()).doesNotContain("level 5").doesNotContain("young");
        assertThat(advice.simulationNotice()).startsWith("PARTIALLY REPAIRED");
        assertThat(jdbc.queryForObject("select narrative_source from ai_agent_runs where id = ?", String.class,
                advice.runId())).isEqualTo("LLM_WITH_REPAIRS");
    }

    @Test
    @DisplayName("A grounded model answer is returned as-is in a single attempt")
    void groundedModelAccepted() throws Exception {
        when(llm.narrate(any(), anyList())).thenReturn(new NarrationResult(
                "One eligible candidate was found.",
                "Asha Rao meets the Java requirement at level 4.",
                List.of(new CandidateNarrative(asha.getId(), "Asha Rao has Java at level 4, which meets the requirement.",
                        List.of("Available at 100% for the full period"), List.of()))));

        StaffingAdviceResponse advice = advise("Is Asha Rao a fit?");

        verify(llm, times(1)).narrate(any(), anyList());
        assertThat(advice.narrativeSource().name()).isEqualTo("LLM");
        assertThat(advice.status().name()).isEqualTo("COMPLETED");
        assertThat(advice.simulationNotice()).isNull();
        assertThat(advice.validation().issues()).isEmpty();
    }

    @Test
    @DisplayName("When the model fails, the run completes with a clearly labelled deterministic fallback")
    void modelFailureFallsBack() throws Exception {
        when(llm.narrate(any(), anyList())).thenThrow(new OllamaClient.LlmException("timeout"));

        StaffingAdviceResponse advice = advise("Is Asha Rao a fit?");

        assertThat(advice.narrativeSource().name()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(advice.status().name()).isEqualTo("COMPLETED_WITH_FALLBACK");
        assertThat(advice.simulationNotice()).startsWith("FALLBACK");
        assertThat(advice.validation().passed()).isTrue();
        assertThat(advice.recommendations().getFirst().explanation()).startsWith("Asha Rao ranks #1");
    }
}
