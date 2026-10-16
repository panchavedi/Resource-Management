package com.talent.server.service.ai.narration;

import java.util.List;

/**
 * Structured narrative produced by a narrator. This is the only part of an
 * agent response that is
 * free text; it is validated against the {@link EvidencePack} before being
 * returned.
 */
public record NarrationResult(String summary, String answer, List<CandidateNarrative> candidates) {

    public record CandidateNarrative(Long employeeId, String explanation, List<String> strengths,
            List<String> considerations) {

        public CandidateNarrative {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            considerations = considerations == null ? List.of() : List.copyOf(considerations);
        }
    }

    public NarrationResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
