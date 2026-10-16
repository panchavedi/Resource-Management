package com.talent.server.dto.response;

import com.talent.server.config.MatchingProperties;
import com.talent.server.enums.MandatorySkillPolicy;

import java.time.Instant;
import java.util.List;

/**
 * Ranking of candidates for a demand, plus the exact criteria used, so the
 * result can be audited.
 *
 * @param candidatePoolSize  employees evaluated (holders of at least one
 *                           mandatory skill, or everyone when
 *                           the demand has no mandatory skills)
 * @param notConsideredCount employees not evaluated because they hold none of
 *                           the mandatory skills
 * @param rankedCandidates   eligible candidates in rank order (plus FLAG-policy
 *                           candidates, marked ineligible)
 * @param excludedCandidates evaluated candidates that failed a hard rule, with
 *                           reasons
 */
public record MatchResultResponse(
        DemandResponse demand,
        Instant evaluatedAt,
        Criteria criteria,
        int candidatePoolSize,
        int notConsideredCount,
        List<CandidateResponse> rankedCandidates,
        List<CandidateResponse> excludedCandidates) {

    public record Criteria(MandatorySkillPolicy mandatorySkillPolicy, MatchingProperties.Weights weights,
            int evidenceFreshnessMonths, int capacityLimitPercent, List<String> rules) {
    }
}
