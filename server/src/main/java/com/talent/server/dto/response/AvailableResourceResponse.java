package com.talent.server.dto.response;

import com.talent.server.enums.EmploymentStatus;
import com.talent.server.service.matching.AvailabilityProfile;

import java.util.List;

/**
 * One row of the resource search: an assignable employee, their availability
 * for the requested
 * window, and the stored skills that matched the filter.
 *
 * @param matchedSkillAverage mean stored level across the filtered skills
 *                            ({@code null} when no skill filter)
 */
public record AvailableResourceResponse(
                Long employeeId,
                String employeeCode,
                String name,
                String jobTitle,
                String department,
                String location,
                boolean remoteEligible,
                EmploymentStatus employmentStatus,
                int capacityPercent,
                AvailabilityProfile availability,
                List<EmployeeSkillResponse> matchedSkills,
                Double matchedSkillAverage) {
}
