package com.talent.server.enums;

/** How an employee's recorded location relates to the demand location. */
public enum LocationFit {
    ANY_LOCATION,
    SAME_LOCATION,
    REMOTE,
    DIFFERENT_LOCATION_REMOTE_ALLOWED,
    DIFFERENT_LOCATION,
    UNKNOWN
}
