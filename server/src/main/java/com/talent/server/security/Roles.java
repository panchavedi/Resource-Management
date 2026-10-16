package com.talent.server.security;

/** {@code @PreAuthorize} expressions shared by the controllers. Reads only require authentication. */
public final class Roles {

    private Roles() {
    }

    /** Create demands, employees, skills; propose allocations; run the staffing agent. */
    public static final String MANAGE = "hasAnyRole('RESOURCE_MANAGER', 'ADMIN')";

    /** Approve or reject pending allocations. */
    public static final String APPROVE = "hasAnyRole('APPROVER', 'ADMIN')";

    /** Cancel allocations (releases capacity). */
    public static final String CANCEL = "hasAnyRole('RESOURCE_MANAGER', 'APPROVER', 'ADMIN')";
}
