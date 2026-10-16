package com.talent.server.enums;

/**
 * Business priority of a project demand. Used for queue ordering, not for candidate scoring
 * (priority is a property of the demand and would add the same value to every candidate).
 */
public enum DemandPriority {
    CRITICAL(1),
    HIGH(2),
    MEDIUM(3),
    LOW(4);

    private final int rank;

    DemandPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
