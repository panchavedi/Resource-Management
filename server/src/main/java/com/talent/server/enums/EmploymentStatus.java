package com.talent.server.enums;

/**
 * Employment status of an employee. Only assignable statuses can receive new allocations.
 */
public enum EmploymentStatus {
    ACTIVE(true),
    BENCH(true),
    ON_LEAVE(false),
    INACTIVE(false);

    private final boolean assignable;

    EmploymentStatus(boolean assignable) {
        this.assignable = assignable;
    }

    public boolean isAssignable() {
        return assignable;
    }
}
