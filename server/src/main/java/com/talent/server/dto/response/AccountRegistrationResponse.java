package com.talent.server.dto.response;

import com.talent.server.entity.RegisteredAccount;
import com.talent.server.enums.AccountStatus;
import java.time.Instant;

public record AccountRegistrationResponse(String username, String displayName, AccountStatus status,
        Instant requestedAt, Instant reviewedAt, String reviewedBy) {
    public static AccountRegistrationResponse of(RegisteredAccount a) {
        return new AccountRegistrationResponse(a.getUsername(), a.getDisplayName(), a.getStatus(),
                a.getRequestedAt(), a.getReviewedAt(), a.getReviewedBy());
    }
}
