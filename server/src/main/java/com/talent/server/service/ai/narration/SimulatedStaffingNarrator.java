package com.talent.server.service.ai.narration;

import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.CandidateResponse.SkillMatch;
import com.talent.server.dto.response.StaffingAdviceResponse.DataGap;
import com.talent.server.dto.response.StaffingAdviceResponse.ReferencedEmployee;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.enums.EvidenceStatus;
import com.talent.server.enums.LocationFit;
import com.talent.server.service.ai.narration.EvidencePack.CandidateEvidence;
import com.talent.server.service.ai.narration.NarrationResult.CandidateNarrative;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministic, template-based narrator. Used when the agent runs in SIMULATED
 * mode, when the LLM
 * is unreachable in AUTO mode, and as the fallback when LLM output fails
 * validation. Every sentence
 * is assembled from evidence fields, so it cannot introduce facts. Responses
 * produced by it are
 * always labelled as simulated or fallback.
 */
@Component
public class SimulatedStaffingNarrator implements StaffingNarrator {

    @Override
    public NarrationResult narrate(EvidencePack evidence, List<String> correctionFeedback) {
        List<CandidateNarrative> narratives = evidence.candidates().stream().map(this::narrate).toList();
        return new NarrationResult(summary(evidence), answer(evidence), narratives);
    }

    private String summary(EvidencePack evidence) {
        var d = evidence.demand();
        String header = "%s for %s needs %d%% from %s to %s.".formatted(d.role(), d.projectName(),
                d.allocationPercent(), d.startDate(), d.endDate());
        if (evidence.candidates().isEmpty()) {
            Map<String, Long> reasons = evidence.exclusions().reasonCounts();
            String why = reasons.isEmpty() ? "no employee holds the mandatory skills"
                    : reasons.entrySet().stream().map(e -> e.getValue() + " × " + e.getKey())
                            .collect(Collectors.joining("; "));
            return header + " No eligible candidate was found among %d evaluated employees (%s)."
                    .formatted(evidence.exclusions().evaluated(), why);
        }
        CandidateResponse top = evidence.candidates().getFirst().candidate();
        long full = evidence.candidates().stream()
                .filter(c -> c.candidate().availability().status() == AvailabilityStatus.FULL).count();
        return header
                + " %d eligible candidate(s) explained; %s ranks first with a score of %s. Full-period availability: %d of %d."
                        .formatted(evidence.candidates().size(), top.name(), fmt(top.totalScore()), full,
                                evidence.candidates().size());
    }

    private CandidateNarrative narrate(CandidateEvidence ce) {
        CandidateResponse c = ce.candidate();
        long met = c.skillMatches().stream().filter(SkillMatch::meetsRequirement).count();
        String explanation = "%s ranks #%d with a score of %s. Meets %d of %d required skills. %s %s".formatted(
                c.name(), c.rank(), fmt(c.totalScore()), met, c.skillMatches().size(),
                c.availability().describe(), c.locationFit().explanation() + ".");

        List<String> strengths = new ArrayList<>();
        c.skillMatches().stream()
                .filter(s -> s.meetsRequirement() && s.actualLevel() != null && s.actualLevel() > s.requiredLevel())
                .limit(2)
                .forEach(s -> strengths.add(
                        "%s at level %d (requires %d)".formatted(s.skillName(), s.actualLevel(), s.requiredLevel())));
        if (c.availability().status() == AvailabilityStatus.FULL) {
            strengths.add("Available at %d%% for the full period".formatted(c.availability().requestedPercent()));
        }
        if (c.locationFit().fit() == LocationFit.SAME_LOCATION) {
            strengths.add("Same location as the demand");
        }
        if (strengths.isEmpty()) {
            strengths.add("Meets every mandatory skill requirement");
        }

        List<String> considerations = new ArrayList<>();
        c.gaps().forEach(g -> considerations.add(g.actualLevel() == null
                ? "%s %s: no stored record (requires level %d)".formatted(g.mandatory() ? "Mandatory" : "Optional",
                        g.skillName(), g.requiredLevel())
                : "%s %s: stored level %d, requires %d".formatted(g.mandatory() ? "Mandatory" : "Optional",
                        g.skillName(), g.actualLevel(), g.requiredLevel())));
        if (c.availability().status() == AvailabilityStatus.PARTIAL) {
            considerations.add("Partial availability: %d of %d days".formatted(c.availability().availableDays(),
                    c.availability().totalDays()));
        }
        c.skillMatches().stream()
                .filter(s -> s.evidenceStatus() == EvidenceStatus.UNVALIDATED
                        || s.evidenceStatus() == EvidenceStatus.STALE)
                .forEach(s -> considerations.add("%s evidence is %s".formatted(s.skillName(),
                        s.evidenceStatus().name().toLowerCase(Locale.ROOT))));
        return new CandidateNarrative(c.employeeId(), explanation, strengths.stream().limit(3).toList(),
                considerations.stream().limit(3).toList());
    }

    private String answer(EvidencePack evidence) {
        if (!evidence.hasQuestion()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (evidence.fabricationRequested()) {
            parts.add(
                    "I can only report what is stored; I cannot assume, invent or estimate proficiency or availability.");
        }
        for (ReferencedEmployee r : evidence.referencedEmployees()) {
            parts.add(describeReferenced(r, evidence.mentionedSkills()));
        }
        if (evidence.referencedEmployees().isEmpty() && !evidence.candidates().isEmpty()) {
            CandidateResponse top = evidence.candidates().getFirst().candidate();
            parts.add("Based on stored records, %s is the strongest eligible match (score %s). %s"
                    .formatted(top.name(), fmt(top.totalScore()), top.availability().describe()));
        }
        List<DataGap> relevant = evidence.dataGaps().stream()
                .filter(g -> g.subject() != null && g.subject().startsWith("question"))
                .toList();
        relevant.forEach(g -> parts.add("Not on record: %s%s.".formatted(
                g.employeeName() == null ? "" : g.employeeName() + " - ", g.detail())));
        if (parts.isEmpty()) {
            parts.add(
                    "The stored evidence does not answer this question directly; see the ranked candidates and data gaps.");
        }
        return String.join(" ", parts);
    }

    private String describeReferenced(ReferencedEmployee r, List<String> mentionedSkills) {
        List<String> facts = new ArrayList<>();
        for (String skill : mentionedSkills) {
            r.storedSkills().stream().filter(s -> s.skillName().equalsIgnoreCase(skill)).findFirst()
                    .ifPresentOrElse(
                            s -> facts.add("%s stored at level %d (%s)".formatted(s.skillName(), s.proficiencyLevel(),
                                    s.evidenceStatus().name().toLowerCase(Locale.ROOT))),
                            () -> facts.add("no stored %s record".formatted(skill)));
        }
        if (mentionedSkills.isEmpty()) {
            r.storedSkills().stream().limit(3)
                    .forEach(s -> facts.add("%s level %d".formatted(s.skillName(), s.proficiencyLevel())));
        }
        String availability = r.availability() == null ? "" : " " + r.availability().describe();
        String ranking = r.inRanking() ? " Ranked #%d for this demand.".formatted(r.rank())
                : " Not in the eligible ranking for this demand.";
        return "%s: %s.%s%s".formatted(r.name(), facts.isEmpty() ? "no stored skills" : String.join(", ", facts),
                availability, ranking);
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
