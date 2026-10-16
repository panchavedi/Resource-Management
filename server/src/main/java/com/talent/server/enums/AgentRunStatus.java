package com.talent.server.enums;

/** Lifecycle of a staffing agent run, persisted for audit. */
public enum AgentRunStatus {
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_FALLBACK,
    FAILED
}
