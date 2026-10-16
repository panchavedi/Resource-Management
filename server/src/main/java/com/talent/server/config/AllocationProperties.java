package com.talent.server.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Allocation business rules.
 *
 * @param maxCapacityPercent organisation-wide ceiling; an employee's effective
 *                           capacity is
 *                           {@code min(employee.capacityPercent, maxCapacityPercent)}
 * @param fourEyesApproval   when true, the requester of an allocation may not
 *                           approve it
 */
@Validated
@ConfigurationProperties("app.allocation")
public record AllocationProperties(
        @Min(1) @Max(100) @DefaultValue("100") int maxCapacityPercent,
        @DefaultValue("true") boolean fourEyesApproval) {
}
