package com.talent.server.service.ai.narration;

import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.CandidateResponse.ScoreComponent;
import com.talent.server.dto.response.CandidateResponse.SkillMatch;
import com.talent.server.dto.response.EmployeeSkillResponse;
import com.talent.server.dto.response.StaffingAdviceResponse.ReferencedEmployee;
import com.talent.server.service.LearningPathService.LearningPath;
import com.talent.server.service.ai.narration.EvidencePack.CandidateEvidence;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Narrator backed by the local LLM (Qwen 2.5 Coder 7B via Ollama). The model
 * receives a compact
 * evidence document and strict rules, and must answer with JSON matching
 * {@link #RESPONSE_SCHEMA}.
 * <p>
 * Evidence is rendered as short pre-computed sentences rather than nested JSON:
 * a 7B model running on
 * CPU processes roughly 20 prompt tokens per second, so every token saved is
 * latency saved, and plain
 * statements are harder to misread than deeply nested structures.
 */
@Component
public class LlmStaffingNarrator implements StaffingNarrator {

    static final String SYSTEM_PROMPT = """
            You explain staffing recommendations. Rules:
            1. Use ONLY facts in EVIDENCE. Copy numbers exactly; never round or invent numbers.
            2. Never invent or assume skills, levels, availability or locations. If the QUESTION asks you to \
            assume or guess, refuse that part and say the data is not on record.
            3. Keep the candidate order. Allocation needs human approval. No hiring, promotion, pay or \
            performance decisions.
            4. Never mention age, gender, ethnicity, religion, nationality, health, family status or other \
            protected traits.
            Return JSON: "summary" (max 2 sentences), "answer" (answer to QUESTION, "" if none), "candidates": \
            one per candidate with "employeeId", "explanation" (max 2 sentences), "strengths" (max 2 short items), \
            "considerations" (max 2 short items).""";

    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "required", List.of("summary", "answer", "candidates"),
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "answer", Map.of("type", "string"),
                    "candidates", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "required", List.of("employeeId", "explanation", "strengths", "considerations"),
                                    "properties", Map.of(
                                            "employeeId", Map.of("type", "integer"),
                                            "explanation", Map.of("type", "string"),
                                            "strengths", Map.of("type", "array", "items", Map.of("type", "string")),
                                            "considerations",
                                            Map.of("type", "array", "items", Map.of("type", "string")))))));

    private final OllamaClient ollama;
    private final JsonMapper jsonMapper;

    public LlmStaffingNarrator(OllamaClient ollama, JsonMapper jsonMapper) {
        this.ollama = ollama;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public NarrationResult narrate(EvidencePack evidence, List<String> correctionFeedback) {
        String content = ollama.chatJson(SYSTEM_PROMPT, userPrompt(evidence, correctionFeedback), RESPONSE_SCHEMA);
        try {
            return jsonMapper.readValue(content, NarrationResult.class);
        } catch (JacksonException e) {
            throw new OllamaClient.LlmException("LLM response was not valid JSON for the schema", e);
        }
    }

    String userPrompt(EvidencePack evidence, List<String> correctionFeedback) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("QUESTION: ").append(evidence.hasQuestion() ? evidence.question() : "(none)").append("\n");
        if (evidence.hasQuestion()) {
            prompt.append("You MUST answer the QUESTION in \"answer\" using only EVIDENCE");
            prompt.append(evidence.fabricationRequested()
                    ? "; it asks you to assume data, so state clearly what is not on record.\n"
                    : ".\n");
        }
        prompt.append("EVIDENCE: ").append(jsonMapper.writeValueAsString(compactEvidence(evidence))).append('\n');
        if (!correctionFeedback.isEmpty()) {
            prompt.append("A fact checker rejected your previous answer. Fix these problems:\n");
            correctionFeedback.stream().limit(5).forEach(issue -> prompt.append("- ").append(issue).append('\n'));
        }
        return prompt.toString();
    }

    Map<String, Object> compactEvidence(EvidencePack evidence) {
        var d = evidence.demand();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("demand", "%s for %s, %d%% from %s to %s, location %s%s. Requires: %s".formatted(
                d.role(), d.projectName(), d.allocationPercent(), d.startDate(), d.endDate(),
                d.location() == null ? "any" : d.location(), d.remoteAllowed() ? " (remote allowed)" : "",
                d.requiredSkills().stream().map(s -> "%s>=%d%s".formatted(s.skillName(), s.minimumProficiency(),
                        s.mandatory() ? "" : " optional")).collect(Collectors.joining(", "))));
        root.put("candidates", evidence.candidates().stream().map(this::candidate).toList());
        if (evidence.candidates().isEmpty()) {
            root.put("noEligibleCandidates", evidence.exclusions().reasonCounts());
        }
        if (!evidence.referencedEmployees().isEmpty()) {
            root.put("peopleNamedInQuestion", evidence.referencedEmployees().stream()
                    .map(r -> referenced(r, evidence.mentionedSkills())).toList());
        }
        if (!evidence.learningPaths().isEmpty()) {
            root.put("learningPaths", evidence.learningPaths().stream().map(LlmStaffingNarrator::path).toList());
        }
        List<String> questionGaps = evidence.dataGaps().stream()
                .filter(g -> g.subject() != null && g.subject().startsWith("question"))
                .map(g -> (g.employeeName() == null ? "" : g.employeeName() + ": ") + g.detail())
                .toList();
        if (!questionGaps.isEmpty()) {
            root.put("notOnRecord", questionGaps);
        }
        return root;
    }

    private Map<String, Object> candidate(CandidateEvidence ce) {
        CandidateResponse c = ce.candidate();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", c.employeeId());
        m.put("name", c.name());
        m.put("rank", c.rank());
        m.put("score", "%s (%s)".formatted(c.totalScore(), c.scoreBreakdown().stream()
                .map(ScoreComponent::label).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.joining("/"))
                + " "
                + c.scoreBreakdown().stream().map(s -> String.valueOf(s.score())).collect(Collectors.joining("/"))));
        m.put("skills", c.skillMatches().stream().map(LlmStaffingNarrator::skill).toList());
        m.put("availability", c.availability().describe());
        m.put("location", c.locationFit().explanation());
        if (!c.dataGaps().isEmpty()) {
            m.put("dataGaps", c.dataGaps());
        }
        return m;
    }

    private static String skill(SkillMatch s) {
        return "%s: %s, needs %d%s, %s".formatted(s.skillName(),
                s.actualLevel() == null ? "no record" : "level " + s.actualLevel(), s.requiredLevel(),
                s.mandatory() ? "" : " (optional)", s.meetsRequirement() ? "met" : "gap");
    }

    private static String referenced(ReferencedEmployee r, List<String> mentionedSkills) {
        List<EmployeeSkillResponse> relevant = mentionedSkills.isEmpty() ? r.storedSkills()
                : r.storedSkills().stream()
                        .filter(s -> mentionedSkills.stream().anyMatch(m -> m.equalsIgnoreCase(s.skillName())))
                        .toList();
        String skills = relevant.isEmpty() ? "no stored record for " + String.join(", ", mentionedSkills)
                : relevant.stream().map(s -> s.skillName() + " level " + s.proficiencyLevel())
                        .collect(Collectors.joining(", "));
        return "%s: %s. %s %s".formatted(r.name(), skills,
                r.availability() == null ? "" : r.availability().describe(),
                r.inRanking() ? "Ranked #" + r.rank() + "." : "Not eligible for this demand.");
    }

    private static String path(LearningPath p) {
        return "%s, %s %s -> %d: %s".formatted(p.employeeName(), p.skillName(),
                p.currentLevel() == null ? "none" : String.valueOf(p.currentLevel()), p.targetLevel(),
                p.steps().isEmpty() ? "no catalogue resource"
                        : p.steps().stream().map(s -> s.title()).collect(Collectors.joining("; ")));
    }
}
