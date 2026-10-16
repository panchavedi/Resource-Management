package com.talent.server.service.matching;

import com.talent.server.entity.Allocation;
import com.talent.server.enums.AllocationStatus;

import java.time.LocalDate;

/** A capacity-holding allocation as seen by the availability calculation. */
public record Booking(Long allocationId, Long demandId, String projectName, LocalDate startDate, LocalDate endDate,
        int allocationPercent, AllocationStatus status) {

    public static Booking of(Allocation allocation) {
        return new Booking(allocation.getId(), allocation.getDemand().getId(), allocation.getDemand().getProjectName(),
                allocation.getStartDate(), allocation.getEndDate(), allocation.getAllocationPercent(),
                allocation.getStatus());
    }

    boolean covers(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    boolean overlaps(LocalDate start, LocalDate end) {
        return !startDate.isAfter(end) && !endDate.isBefore(start);
    }
}
