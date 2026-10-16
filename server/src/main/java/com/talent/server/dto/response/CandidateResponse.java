package com.talent.server.dto.response;

import com.talent.server.enums.EmploymentStatus;
import com.talent.server.enums.EvidenceSource;
import com.talent.server.enums.EvidenceStatus;
import com.talent.server.enums.LocationFit;
import com.talent.server.service.matching.AvailabilityProfile;

import java.time.LocalDate;
import java.util.List;

/**
 * Full, explainable evaluation of one candidate against one demand. Every
 * number is derived from
 * stored records; {@code dataGaps} discloses what is missing instead of
 * guessing it.
 *
 * @param rank                 1-based position in the ranking; {@code null} for
 *                             excluded candidates
 * @param eligible             passes every hard rule (status, mandatory skills,
 *                             some availability)
 * @param meetsMandatorySkills all mandatory skills present at the required
 *                             level
 * @param exclusionReasons     why the candidate is not eligible (empty when
 *                             eligible)
 * @param flags                notable but non-blocking facts (partial
 *                             availability, stale evidence, …)
 */
public record CandidateResponse(
        Long employeeId,
        String employeeCode,
        String name,
        String jobTitle,
        String department,
        String location,
        EmploymentStatus employmentStatus,
        Integer rank,
        boolean eligible,
        boolean meetsMandatorySkills,
        double totalScore,
        List<ScoreComponent> scoreBreakdown,
        List<SkillMatch> skillMatches,
        List<SkillGap> gaps,
        AvailabilityProfile availability,
        LocationAssessment locationFit,
        List<String> flags,
        List<String> dataGaps,
        List<String> exclusionReasons) {

    /**
     * One factor of the total score: {@code weightedScore = score × weight}.
     */
    public record ScoreComponent(String factor, String label, double weight, double score, double weightedScore,
            String explanation) {
    }

    /**
     * Comparison of one required skill with the stored proficiency.
     *
     * @param actualLevel stored level, {@code null} when there is no record
     * @param points      0–1 contribution used in the skill score (formula in
     *                    {@code explanation})
     */
    public record SkillMatch(Long skillId, String skillName, String category, int requiredLevel, boolean mandatory,
            Integer actualLevel, boolean meetsRequirement, double points, String explanation,
            String evidence, EvidenceSource evidenceSource, LocalDate lastValidatedAt,
            EvidenceStatus evidenceStatus) {
    }

    /** A required skill the candidate does not have at the required level. */
    public record SkillGap(Long skillId, String skillName, String category, boolean mandatory, int requiredLevel,
            Integer actualLevel, int levelsShort) {
    }

    public record LocationAssessment(String employeeLocation, String demandLocation, boolean remoteAllowed,
            boolean employeeRemoteEligible, LocationFit fit, double score,
            String explanation) {
    }

    public CandidateResponse withRank(Integer newRank) {
        return new CandidateResponse(employeeId, employeeCode, name, jobTitle, department, location, employmentStatus,
                newRank, eligible, meetsMandatorySkills, totalScore, scoreBreakdown, skillMatches, gaps, availability,
                locationFit, flags, dataGaps, exclusionReasons);
    }

    public double factorScore(String factor) {
        return scoreBreakdown.stream().filter(c -> c.factor().equals(factor))
                .mapToDouble(ScoreComponent::score).findFirst().orElse(0);
    }
}
