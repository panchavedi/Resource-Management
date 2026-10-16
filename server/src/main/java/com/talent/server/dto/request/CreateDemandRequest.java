package com.talent.server.dto.request;

import com.talent.server.dto.validation.DateRange;
import com.talent.server.dto.validation.ValidDateRange;
import com.talent.server.enums.DemandPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Demand intake. Role, a valid date range and the allocation percentage are
 * mandatory.
 *
 * @param location      office location; omit for a location-agnostic demand
 * @param remoteAllowed whether remote staffing is acceptable when locations
 *                      differ (default false)
 * @param headcount     number of people needed (default 1)
 */
@ValidDateRange
public record CreateDemandRequest(
                @NotBlank @Size(max = 120) String projectName,
                @NotBlank @Size(max = 120) String role,
                @Size(max = 2000) String description,
                @NotNull @FutureOrPresent LocalDate startDate,
                @NotNull LocalDate endDate,
                @Size(max = 80) String location,
                Boolean remoteAllowed,
                @NotNull @Min(1) @Max(100) Integer allocationPercent,
                @Min(1) @Max(50) Integer headcount,
                @NotNull DemandPriority priority,
                @Size(max = 20) List<@Valid @NotNull SkillRequirementRequest> requiredSkills) implements DateRange {

        public record SkillRequirementRequest(
                        @NotNull Long skillId,
                        @NotNull @Min(1) @Max(5) Integer minimumProficiency,
                        Boolean mandatory) {
        }
}
