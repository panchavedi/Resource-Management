package com.talent.server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Goal for the staffing agent: "recommend and explain candidates for this
 * demand".
 *
 * @param question                      optional free-text follow-up (answered
 *                                      only from stored evidence)
 * @param topN                          candidates to explain (default from
 *                                      configuration)
 * @param proposeEmployeeId             when set, the agent submits a
 *                                      PENDING_APPROVAL allocation for this
 *                                      employee after re-validating
 *                                      eligibility; a human must approve it
 * @param recordTrainingRecommendations persist learning-path suggestions for
 *                                      the explained candidates' gaps
 */
public record StaffingAdviceRequest(
                @NotNull Long demandId,
                @Size(max = 1000) String question,
                @Min(1) @Max(10) Integer topN,
                Long proposeEmployeeId,
                Boolean recordTrainingRecommendations) {
}
