package com.talent.server.enums;

/** Application roles. Authorities are exposed to Spring Security as {@code ROLE_<name>}. */
public enum Role {
    RESOURCE_MANAGER,
    APPROVER,
    ADMIN
}
