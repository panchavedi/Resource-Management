package com.talent.server.controller;

import com.talent.server.dto.request.LoginRequest;
import com.talent.server.dto.response.AuthResponse;
import com.talent.server.dto.request.ManagerAccessRequest;
import com.talent.server.dto.request.RegisterAccountRequest;
import com.talent.server.dto.response.ManagedAccountResponse;
import com.talent.server.dto.response.AccountRegistrationResponse;
import com.talent.server.config.SecurityProperties;
import com.talent.server.entity.ManagerAccessGrant;
import com.talent.server.repository.ManagerAccessGrantRepository;
import com.talent.server.repository.RegisteredAccountRepository;
import com.talent.server.service.AccountRegistrationService;
import com.talent.server.enums.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.security.LoginAttemptService;
import com.talent.server.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;
    private final SecurityProperties securityProperties;
    private final ManagerAccessGrantRepository managerGrants;
    private final Clock clock;
    private final AccountRegistrationService registrationService;
    private final RegisteredAccountRepository registeredAccounts;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService,
            LoginAttemptService loginAttempts, SecurityProperties securityProperties,
            ManagerAccessGrantRepository managerGrants, Clock clock,
            AccountRegistrationService registrationService, RegisteredAccountRepository registeredAccounts) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.securityProperties = securityProperties;
        this.managerGrants = managerGrants;
        this.clock = clock;
        this.registrationService = registrationService;
        this.registeredAccounts = registeredAccounts;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a bearer token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        if (loginAttempts.isLocked(request.username())) {
            throw new ApiException(ErrorCode.ACCOUNT_TEMPORARILY_LOCKED,
                    "Too many failed attempts. Try again in a few minutes.");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username().trim(), request.password()));
            loginAttempts.recordSuccess(request.username());
            log.info("User {} signed in", authentication.getName());
            return tokenService.issue(authentication);
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(request.username());
            log.info("Failed sign-in for {}", request.username());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Current user from the token")
    public AuthResponse.UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        return TokenService.userInfo(jwt);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a manager account; an approver or administrator must approve it before sign-in")
    public AccountRegistrationResponse register(@Valid @RequestBody RegisterAccountRequest request) {
        return registrationService.register(request);
    }

    @GetMapping("/registrations")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "List manager account requests waiting for approval")
    public List<AccountRegistrationResponse> pendingRegistrations() {
        return registrationService.pending();
    }

    @PutMapping("/registrations/{username}/{decision}")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    @Operation(summary = "Approve or reject a manager account request")
    public AccountRegistrationResponse reviewRegistration(@PathVariable String username,
            @PathVariable String decision, @AuthenticationPrincipal Jwt actor) {
        if (!decision.equalsIgnoreCase("approve") && !decision.equalsIgnoreCase("reject"))
            throw new com.talent.server.exception.ApiException(com.talent.server.exception.ErrorCode.MALFORMED_REQUEST,
                    "Decision must be approve or reject");
        return registrationService.review(username, decision.equalsIgnoreCase("approve"), actor.getSubject());
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List operator-configured accounts and manager access")
    public List<ManagedAccountResponse> accounts() {
        List<ManagedAccountResponse> configured = securityProperties.users().stream().map(account -> {
            List<Role> roles = new ArrayList<>(account.roles());
            boolean granted = managerGrants.existsByUsernameIgnoreCase(account.username());
            if ((granted || account.roles().contains(Role.ADMIN)) && !roles.contains(Role.RESOURCE_MANAGER)) roles.add(Role.RESOURCE_MANAGER);
            return new ManagedAccountResponse(account.username(), account.displayName(), roles,
                    roles.contains(Role.RESOURCE_MANAGER), account.roles().contains(Role.RESOURCE_MANAGER) || account.roles().contains(Role.ADMIN));
        }).toList();
        List<ManagedAccountResponse> registered = registeredAccounts.findAllByOrderByRequestedAtDesc().stream()
                .filter(a -> a.getStatus() == com.talent.server.enums.AccountStatus.ACTIVE)
                .map(a -> new ManagedAccountResponse(a.getUsername(), a.getDisplayName(), List.of(Role.RESOURCE_MANAGER), true, true))
                .toList();
        return java.util.stream.Stream.concat(configured.stream(), registered.stream()).toList();
    }

    @PutMapping("/accounts/{username}/manager-access")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Grant or revoke manager access for a configured account")
    public ManagedAccountResponse managerAccess(@PathVariable String username,
            @jakarta.validation.Valid @RequestBody ManagerAccessRequest request, @AuthenticationPrincipal Jwt actor) {
        SecurityProperties.UserAccount account = securityProperties.users().stream()
                .filter(a -> a.username().equalsIgnoreCase(username.trim())).findFirst()
                .orElseThrow(() -> com.talent.server.exception.ApiException.notFound("Configured account", username));
        boolean baselineManager = account.roles().contains(Role.RESOURCE_MANAGER) || account.roles().contains(Role.ADMIN);
        if (request.enabled()) {
            if (!baselineManager && !managerGrants.existsByUsernameIgnoreCase(account.username()))
                managerGrants.save(new ManagerAccessGrant(account.username(), actor.getSubject(), clock.instant()));
        } else {
            managerGrants.deleteByUsernameIgnoreCase(account.username());
        }
        List<Role> roles = new ArrayList<>(account.roles());
        boolean granted = managerGrants.existsByUsernameIgnoreCase(account.username());
        if (granted && !roles.contains(Role.RESOURCE_MANAGER)) roles.add(Role.RESOURCE_MANAGER);
        if ((granted || baselineManager) && !roles.contains(Role.RESOURCE_MANAGER)) roles.add(Role.RESOURCE_MANAGER);
        return new ManagedAccountResponse(account.username(), account.displayName(), roles,
                baselineManager || granted, baselineManager);
    }
}
