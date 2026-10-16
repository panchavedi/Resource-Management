package com.talent.server.dto.request;

import com.talent.server.enums.EmploymentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateEmploymentStatusRequest(@NotNull EmploymentStatus employmentStatus) {
}
