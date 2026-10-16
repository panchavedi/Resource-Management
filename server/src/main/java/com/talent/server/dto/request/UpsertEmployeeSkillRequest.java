package com.talent.server.dto.request;

import com.talent.server.enums.EvidenceSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Records (or re-assesses) an employee's proficiency in a skill.
 *
 * @param lastValidatedAt date the level was validated; omit when not validated
 *                        (it is then disclosed as unvalidated)
 */
public record UpsertEmployeeSkillRequest(
                @NotNull Long skillId,
                @NotNull @Min(1) @Max(5) Integer proficiencyLevel,
                @Size(max = 500) String evidence,
                @NotNull EvidenceSource evidenceSource,
                @PastOrPresent LocalDate lastValidatedAt) {
}
