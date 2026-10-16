package com.talent.server.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in every error response ({@code code} field).
 * Clients should branch on the code, never on the human-readable message.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_TEMPORARILY_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    ALLOCATION_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_DATE_RANGE(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_CONTENT),
    DEMAND_NOT_OPEN(HttpStatus.UNPROCESSABLE_CONTENT),
    HEADCOUNT_FILLED(HttpStatus.UNPROCESSABLE_CONTENT),
    CANDIDATE_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT),
    CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
    SELF_APPROVAL_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
