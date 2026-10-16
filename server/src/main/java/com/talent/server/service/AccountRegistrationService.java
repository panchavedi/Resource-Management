package com.talent.server.service;

import com.talent.server.config.SecurityProperties;
import com.talent.server.dto.request.RegisterAccountRequest;
import com.talent.server.dto.response.AccountRegistrationResponse;
import com.talent.server.entity.RegisteredAccount;
import com.talent.server.enums.AccountStatus;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.repository.RegisteredAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AccountRegistrationService {
    private final RegisteredAccountRepository accounts;
    private final SecurityProperties security;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AccountRegistrationService(RegisteredAccountRepository accounts, SecurityProperties security,
            PasswordEncoder passwords, Clock clock) {
        this.accounts = accounts;
        this.security = security;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public AccountRegistrationResponse register(RegisterAccountRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        boolean reserved = security.users().stream().anyMatch(u -> u.username().equalsIgnoreCase(username));
        if (reserved || accounts.existsByUsernameIgnoreCase(username))
            throw ApiException.duplicate("That username is already in use");
        RegisteredAccount account = accounts.save(new RegisteredAccount(username, request.displayName().trim(),
                passwords.encode(request.password()), clock.instant()));
        return AccountRegistrationResponse.of(account);
    }

    public List<AccountRegistrationResponse> pending() {
        return accounts.findAllByStatusOrderByRequestedAtAsc(AccountStatus.PENDING_APPROVAL).stream()
                .map(AccountRegistrationResponse::of).toList();
    }

    @Transactional
    public AccountRegistrationResponse review(String username, boolean approve, String reviewer) {
        RegisteredAccount account = accounts.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("Account request", username));
        if (account.getStatus() != AccountStatus.PENDING_APPROVAL)
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "This account request has already been reviewed");
        account.review(approve ? AccountStatus.ACTIVE : AccountStatus.REJECTED, reviewer, clock.instant());
        return AccountRegistrationResponse.of(account);
    }
}
