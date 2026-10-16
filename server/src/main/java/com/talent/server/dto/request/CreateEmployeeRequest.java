package com.talent.server.dto.request;

import com.talent.server.enums.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * New employee. Only staffing-relevant facts are accepted; no protected
 * characteristics.
 *
 * @param capacityPercent maximum allocatable percentage (default 100, e.g. 50
 *                        for part-time)
 */
public record CreateEmployeeRequest(
                @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "must be 3-20 characters: A-Z, 0-9 or '-'") String employeeCode,
                @NotBlank @Size(max = 120) String name,
                @NotBlank @Email @Size(max = 160) String email,
                @Size(max = 120) String jobTitle,
                @NotBlank @Size(max = 80) String department,
                @Size(max = 80) String location,
                Boolean remoteEligible,
                @NotNull EmploymentStatus employmentStatus,
                @Min(1) @Max(100) Integer capacityPercent) {
}
