package com.talent.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import com.talent.server.enums.Role;

import java.time.Duration;
import java.util.List;

/**
 * Authentication settings. Secrets are supplied through environment variables
 * (see {@code .env.example});
 * nothing secret is committed.
 *
 * @param jwtSecret        HS256 signing key, at least 32 bytes. When blank a
 *                         random key is generated at
 *                         startup (tokens then do not survive a restart) and a
 *                         warning is logged.
 * @param tokenTtl         lifetime of issued access tokens
 * @param users            demo user directory; a user whose password is blank
 *                         is not registered
 * @param maxLoginAttempts failed logins allowed before the account is locked
 *                         for {@code lockDuration}
 */
@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
                String jwtSecret,
                @NotBlank @DefaultValue("talent-management") String issuer,
                @NotNull @DefaultValue("8h") Duration tokenTtl,
                @Min(1) @DefaultValue("5") int maxLoginAttempts,
                @NotNull @DefaultValue("5m") Duration lockDuration,
                @DefaultValue List<@Valid UserAccount> users) {

        public record UserAccount(
                        @NotBlank String username,
                        @NotBlank String displayName,
                        String password,
                        @NotEmpty List<Role> roles) {
        }
}
