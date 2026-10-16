package com.talent.server.enums;

/**
 * Trust level of the evidence behind a stored proficiency.
 * VALIDATED: validated within the freshness window. STALE: validated, but longer ago than the window.
 * UNVALIDATED: never validated (e.g. self-declared). MISSING: no record for the skill at all.
 */
public enum EvidenceStatus {
    VALIDATED,
    STALE,
    UNVALIDATED,
    MISSING
}
