package com.talent.server.dto.request;

import com.talent.server.dto.validation.DateRange;
import com.talent.server.dto.validation.ValidDateRange;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Proposes an allocation (status PENDING_APPROVAL). Dates and percentage
 * default to the demand's.
 * Send an {@code Idempotency-Key} header to make retries safe.
 */
@ValidDateRange
public record CreateAllocationRequest(
                @NotNull Long employeeId,
                @NotNull Long demandId,
                LocalDate startDate,
                LocalDate endDate,
                @Min(1) @Max(100) Integer allocationPercent,
                @Size(max = 500) String note) implements DateRange {
}
