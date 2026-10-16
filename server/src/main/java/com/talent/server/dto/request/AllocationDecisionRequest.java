package com.talent.server.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Approve / reject payload. The approver identity comes from the authenticated
 * token, never the body.
 */
public record AllocationDecisionRequest(@Size(max = 500) String note) {
}
