package com.talent.server.dto.response;

import com.talent.server.enums.EmploymentStatus;

import java.time.Instant;
import java.util.List;

public record EmployeeResponse(
                Long id,
                String employeeCode,
                String name,
                String email,
                String jobTitle,
                String department,
                String location,
                boolean remoteEligible,
                EmploymentStatus employmentStatus,
                int capacityPercent,
                List<EmployeeSkillResponse> skills,
                Instant createdAt,
                Instant updatedAt) {
}
