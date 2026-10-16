package com.talent.server.enums;

/**
 * Availability of an employee for a requested date range and allocation percentage.
 * FULL: the requested percentage fits on every day. PARTIAL: it fits on some days, or some
 * (but not enough) capacity is free. NONE: no free capacity on any day of the range.
 */
public enum AvailabilityStatus {
    FULL,
    PARTIAL,
    NONE
}
