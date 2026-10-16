package com.talent.server.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Allocation lifecycle.
 * <pre>
 * PENDING_APPROVAL → APPROVED → CANCELLED
 *                  ↘ REJECTED
 * PENDING_APPROVAL → CANCELLED (withdrawn before decision)
 * </pre>
 * PENDING_APPROVAL and APPROVED both reserve capacity, so two proposals can never
 * jointly overbook an employee. Approval re-validates every rule at commit time.
 */
public enum AllocationStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CANCELLED;

    /** Statuses that consume an employee's capacity. */
    public static final Set<AllocationStatus> CAPACITY_HOLDING = EnumSet.of(PENDING_APPROVAL, APPROVED);

    public boolean holdsCapacity() {
        return CAPACITY_HOLDING.contains(this);
    }
}
