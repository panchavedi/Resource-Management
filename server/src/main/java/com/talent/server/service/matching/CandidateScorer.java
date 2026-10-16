package com.talent.server.service.matching;

import com.talent.server.config.MatchingProperties;
import com.talent.server.dto.response.CandidateResponse;
import com.talent.server.dto.response.CandidateResponse.LocationAssessment;
import com.talent.server.dto.response.CandidateResponse.ScoreComponent;
import com.talent.server.dto.response.CandidateResponse.SkillGap;
import com.talent.server.dto.response.CandidateResponse.SkillMatch;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.enums.EvidenceStatus;
import com.talent.server.enums.LocationFit;
import com.talent.server.enums.MandatorySkillPolicy;
import com.talent.server.service.matching.MatchingModels.CandidateProfile;
import com.talent.server.service.matching.MatchingModels.DemandProfile;
import com.talent.server.service.matching.MatchingModels.Requirement;
import com.talent.server.service.matching.MatchingModels.SkillRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, explainable eligibility and scoring rules. Pure: no database,
 * no AI.
 *
 * <h2>Eligibility (hard rules, applied in order)</h2>
 * <ol>
 * <li>Employment status must be assignable (ACTIVE or BENCH).</li>
 * <li>Every mandatory skill must be stored at or above the minimum level
 * (policy EXCLUDE removes the candidate, FLAG keeps them ranked last and marked
 * ineligible).</li>
 * <li>Some capacity must be free in the requested period (availability NONE
 * excludes).</li>
 * </ol>
 *
 * <h2>Score (0–100) = Σ factor score × weight</h2>
 * <ul>
 * <li><b>Skill fit</b> – weighted mean of per-skill points (mandatory weight 2,
 * optional 1).
 * Met: 0.70 + 0.30 × (level − required) / (5 − required). Below: 0.70 × level /
 * required. Missing: 0.</li>
 * <li><b>Availability</b> – coverage: average over days of min(1, free /
 * requested).</li>
 * <li><b>Location</b> – same 100, remote-eligible on a remote-allowed demand
 * 80, different but remote
 * allowed 40, different onsite 0, location-agnostic demand 100, unknown
 * location 0.</li>
 * <li><b>Evidence quality</b> – share of matched skills with fresh validation
 * (stale counts half).</li>
 * </ul>
 * Ties are broken by skill fit, then availability, then employee code, so
 * rankings are reproducible.
 */
@Component
public class CandidateScorer {

    public static final String FACTOR_SKILL = "SKILL_FIT";
    public static final String FACTOR_AVAILABILITY = "AVAILABILITY";
    public static final String FACTOR_LOCATION = "LOCATION";
    public static final String FACTOR_EVIDENCE = "EVIDENCE_QUALITY";

    /**
     * Ranking order: eligible first, then score, then deterministic tie-breakers.
     */
    public static final Comparator<CandidateResponse> RANKING = Comparator
            .comparing(CandidateResponse::eligible).reversed()
            .thenComparing(Comparator.comparingDouble(CandidateResponse::totalScore).reversed())
            .thenComparing(Comparator.comparingDouble((CandidateResponse c) -> c.factorScore(FACTOR_SKILL)).reversed())
            .thenComparing(
                    Comparator.comparingDouble((CandidateResponse c) -> c.factorScore(FACTOR_AVAILABILITY)).reversed())
            .thenComparing(CandidateResponse::employeeCode);

    private static final double MET_BASE = 0.70;
    private static final int MAX_LEVEL = 5;

    private final MatchingProperties properties;
    private final EvidenceClassifier evidenceClassifier;

    public CandidateScorer(MatchingProperties properties, EvidenceClassifier evidenceClassifier) {
        this.properties = properties;
        this.evidenceClassifier = evidenceClassifier;
    }

    public MatchingProperties properties() {
        return properties;
    }

    public CandidateResponse evaluate(DemandProfile demand, CandidateProfile candidate,
            AvailabilityProfile availability) {
        List<SkillMatch> matches = new ArrayList<>();
        List<SkillGap> gaps = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        List<String> dataGaps = new ArrayList<>();
        List<String> exclusions = new ArrayList<>();

        // ---- skills
        double weightedPoints = 0;
        double totalWeight = 0;
        boolean mandatoryMet = true;
        int matched = 0;
        double evidencePoints = 0;
        for (Requirement req : demand.requirements()) {
            SkillRecord record = candidate.skillsById().get(req.skillId());
            SkillMatch match = matchSkill(req, record);
            matches.add(match);
            double weight = req.mandatory() ? 2.0 : 1.0;
            weightedPoints += match.points() * weight;
            totalWeight += weight;
            if (!match.meetsRequirement()) {
                int actual = record == null ? 0 : record.level();
                gaps.add(new SkillGap(req.skillId(), req.skillName(), req.category(), req.mandatory(),
                        req.minimumLevel(), record == null ? null : record.level(), req.minimumLevel() - actual));
                if (req.mandatory()) {
                    mandatoryMet = false;
                    exclusions.add(record == null
                            ? "Missing mandatory skill %s (requires level %d, no stored record)"
                                    .formatted(req.skillName(), req.minimumLevel())
                            : "Mandatory skill %s below required level (stored %d, requires %d)"
                                    .formatted(req.skillName(), record.level(), req.minimumLevel()));
                }
            }
            if (record == null) {
                dataGaps.add("No stored proficiency record for %s".formatted(req.skillName()));
            } else {
                matched++;
                evidencePoints += switch (match.evidenceStatus()) {
                    case VALIDATED -> 1.0;
                    case STALE -> 0.5;
                    default -> 0.0;
                };
                if (match.evidenceStatus() == EvidenceStatus.UNVALIDATED) {
                    dataGaps.add("%s level %d has never been validated (source: %s)"
                            .formatted(req.skillName(), record.level(), record.evidenceSource()));
                } else if (match.evidenceStatus() == EvidenceStatus.STALE) {
                    dataGaps.add("%s was last validated on %s, older than %d months"
                            .formatted(req.skillName(), record.lastValidatedAt(),
                                    properties.evidenceFreshnessMonths()));
                }
            }
        }
        double skillScore = totalWeight == 0 ? 100.0 : round1(weightedPoints / totalWeight * 100.0);
        double evidenceScore = matched == 0 ? 0.0 : round1(evidencePoints / matched * 100.0);

        // ---- availability
        double availabilityScore = availability.coveragePercent();
        if (availability.status() == AvailabilityStatus.NONE) {
            exclusions.add(availability.describe());
        } else if (availability.status() == AvailabilityStatus.PARTIAL) {
            flags.add(availability.describe());
        }

        // ---- employment status
        if (!candidate.employmentStatus().isAssignable()) {
            exclusions.addFirst("Employment status %s is not assignable".formatted(candidate.employmentStatus()));
        }

        // ---- location
        LocationAssessment location = assessLocation(demand, candidate);
        if (location.fit() == LocationFit.UNKNOWN) {
            dataGaps.add("Work location is not recorded");
        } else if (location.fit() == LocationFit.DIFFERENT_LOCATION) {
            flags.add(location.explanation());
        }

        MatchingProperties.Weights w = properties.weights();
        List<ScoreComponent> breakdown = List.of(
                component(FACTOR_SKILL, "Skill fit", w.skill(), skillScore, explainSkillScore(matches)),
                component(FACTOR_AVAILABILITY, "Availability", w.availability(), availabilityScore,
                        availability.describe()),
                component(FACTOR_LOCATION, "Location fit", w.location(), location.score(), location.explanation()),
                component(FACTOR_EVIDENCE, "Evidence quality", w.evidence(), evidenceScore,
                        matched == 0 ? "No stored records for the required skills"
                                : "%d of %d matched skills have fresh validated evidence".formatted(
                                        matches.stream().filter(m -> m.evidenceStatus() == EvidenceStatus.VALIDATED)
                                                .count(),
                                        matched)));
        double total = round1(breakdown.stream().mapToDouble(ScoreComponent::weightedScore).sum());

        boolean eligible = exclusions.isEmpty();
        if (!mandatoryMet && properties.mandatorySkillPolicy() == MandatorySkillPolicy.FLAG) {
            flags.add("Flagged: does not meet all mandatory skills (policy FLAG keeps the candidate visible)");
        }

        return new CandidateResponse(candidate.employeeId(), candidate.employeeCode(), candidate.name(),
                candidate.jobTitle(), candidate.department(), candidate.location(), candidate.employmentStatus(), null,
                eligible, mandatoryMet, total, breakdown, List.copyOf(matches), List.copyOf(gaps), availability,
                location, List.copyOf(flags), List.copyOf(dataGaps), List.copyOf(exclusions));
    }

    /**
     * Whether an evaluated candidate belongs in the ranked list (as opposed to the
     * exclusion list).
     * Under FLAG policy a candidate whose only problem is a mandatory skill stays
     * ranked (marked ineligible).
     */
    public boolean isRanked(CandidateResponse candidate) {
        if (candidate.eligible()) {
            return true;
        }
        if (properties.mandatorySkillPolicy() != MandatorySkillPolicy.FLAG) {
            return false;
        }
        boolean otherBlockers = candidate.availability().status() == AvailabilityStatus.NONE
                || !candidate.employmentStatus().isAssignable();
        return !candidate.meetsMandatorySkills() && !otherBlockers;
    }

    SkillMatch matchSkill(Requirement req, SkillRecord record) {
        if (record == null) {
            return new SkillMatch(req.skillId(), req.skillName(), req.category(), req.minimumLevel(), req.mandatory(),
                    null, false, 0.0, "No stored record: 0 points", null, null, null, EvidenceStatus.MISSING);
        }
        int level = record.level();
        int min = req.minimumLevel();
        boolean met = level >= min;
        double points;
        String explanation;
        if (met) {
            double headroom = min >= MAX_LEVEL ? 1.0 : (double) (level - min) / (MAX_LEVEL - min);
            points = MET_BASE + (1 - MET_BASE) * headroom;
            explanation = "Meets level %d with %d: 0.70 + 0.30 × %d/%d = %.2f"
                    .formatted(min, level, level - min, Math.max(1, MAX_LEVEL - min), points);
        } else {
            points = MET_BASE * level / min;
            explanation = "Below level %d with %d: 0.70 × %d/%d = %.2f".formatted(min, level, level, min, points);
        }
        return new SkillMatch(req.skillId(), req.skillName(), req.category(), min, req.mandatory(), level, met,
                round2(points), explanation, record.evidence(), record.evidenceSource(), record.lastValidatedAt(),
                evidenceClassifier.classify(record.lastValidatedAt()));
    }

    LocationAssessment assessLocation(DemandProfile demand, CandidateProfile candidate) {
        String demandLoc = demand.location();
        String empLoc = candidate.location();
        LocationFit fit;
        double score;
        String explanation;
        if (demandLoc == null || demandLoc.isBlank()) {
            fit = LocationFit.ANY_LOCATION;
            score = 100;
            explanation = "Demand has no location requirement";
        } else if (empLoc == null || empLoc.isBlank()) {
            fit = LocationFit.UNKNOWN;
            score = 0;
            explanation = "Employee location not recorded; demand is in %s".formatted(demandLoc);
        } else if (empLoc.trim().toLowerCase(Locale.ROOT).equals(demandLoc.trim().toLowerCase(Locale.ROOT))) {
            fit = LocationFit.SAME_LOCATION;
            score = 100;
            explanation = "Based in %s, same as the demand".formatted(empLoc);
        } else if (demand.remoteAllowed() && candidate.remoteEligible()) {
            fit = LocationFit.REMOTE;
            score = 80;
            explanation = "Based in %s; demand in %s allows remote work and employee is remote-eligible"
                    .formatted(empLoc, demandLoc);
        } else if (demand.remoteAllowed()) {
            fit = LocationFit.DIFFERENT_LOCATION_REMOTE_ALLOWED;
            score = 40;
            explanation = "Based in %s; demand in %s allows remote work but employee is not marked remote-eligible"
                    .formatted(empLoc, demandLoc);
        } else {
            fit = LocationFit.DIFFERENT_LOCATION;
            score = 0;
            explanation = "Based in %s; demand requires onsite presence in %s".formatted(empLoc, demandLoc);
        }
        return new LocationAssessment(empLoc, demandLoc, demand.remoteAllowed(), candidate.remoteEligible(), fit,
                score, explanation);
    }

    private static String explainSkillScore(List<SkillMatch> matches) {
        if (matches.isEmpty()) {
            return "Demand lists no skill requirements";
        }
        long met = matches.stream().filter(SkillMatch::meetsRequirement).count();
        return "%d of %d required skills met; weighted mean of per-skill points (mandatory ×2)".formatted(met,
                matches.size());
    }

    private static ScoreComponent component(String factor, String label, double weight, double score,
            String explanation) {
        return new ScoreComponent(factor, label, weight, score, round1(score * weight), explanation);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
