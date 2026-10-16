package com.talent.server.dto.response;

import com.talent.server.entity.Allocation;
import com.talent.server.entity.AllocationEvent;
import com.talent.server.enums.AllocationEventType;
import com.talent.server.enums.AllocationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param history full state history; populated on single-allocation reads,
 *                {@code null} in lists
 */
public record AllocationResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long demandId,
        String projectName,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        int allocationPercent,
        AllocationStatus status,
        String requestedBy,
        Instant requestedAt,
        String requestNote,
        String decidedBy,
        Instant decidedAt,
        String decisionNote,
        String cancelledBy,
        Instant cancelledAt,
        String cancellationReason,
        long version,
        List<HistoryEntry> history) {

    public record HistoryEntry(AllocationEventType eventType, AllocationStatus fromStatus, AllocationStatus toStatus,
            String actor, String note, Instant occurredAt) {

        static HistoryEntry of(AllocationEvent e) {
            return new HistoryEntry(e.getEventType(), e.getFromStatus(), e.getToStatus(), e.getActor(), e.getNote(),
                    e.getOccurredAt());
        }
    }

    public static AllocationResponse summary(Allocation a) {
        return of(a, null);
    }

    public static AllocationResponse detailed(Allocation a) {
        return of(a, a.getEvents().stream().map(HistoryEntry::of).toList());
    }

    private static AllocationResponse of(Allocation a, List<HistoryEntry> history) {
        return new AllocationResponse(a.getId(), a.getEmployee().getId(), a.getEmployee().getEmployeeCode(),
                a.getEmployee().getName(), a.getDemand().getId(), a.getDemand().getProjectName(),
                a.getDemand().getRole(), a.getStartDate(), a.getEndDate(), a.getAllocationPercent(), a.getStatus(),
                a.getRequestedBy(), a.getRequestedAt(), a.getRequestNote(), a.getDecidedBy(), a.getDecidedAt(),
                a.getDecisionNote(), a.getCancelledBy(), a.getCancelledAt(), a.getCancellationReason(),
                a.getVersion(), history);
    }
}
