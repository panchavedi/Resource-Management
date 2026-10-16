package com.talent.server.enums;

/**
 * Demand lifecycle. OPEN and PARTIALLY_FILLED demands accept new allocation proposals.
 * <pre>
 * OPEN ⇄ PARTIALLY_FILLED ⇄ FILLED      (driven by approved allocations vs. headcount)
 * OPEN | PARTIALLY_FILLED | FILLED → CANCELLED
 * </pre>
 */
public enum DemandStatus {
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED;

    public boolean acceptsAllocations() {
        return this == OPEN || this == PARTIALLY_FILLED;
    }
}
