package com.talent.server.security;

import com.talent.server.config.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles brute-force attempts: after {@code maxLoginAttempts} consecutive
 * failures an account is
 * locked for {@code lockDuration}. In-memory by design for a single instance; a
 * multi-instance
 * deployment would back this with Redis or the database.
 */
@Component
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final SecurityProperties properties;
    private final Clock clock;

    public LoginAttemptService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Attempts a = attempts.get(key(username));
        return a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(clock.instant());
    }

    public void recordFailure(String username) {
        attempts.compute(key(username), (k, current) -> {
            int failures = (current == null
                    || current.lockedUntil() != null && !current.lockedUntil().isAfter(clock.instant()))
                            ? 1
                            : current.failures() + 1;
            Instant lockedUntil = failures >= properties.maxLoginAttempts()
                    ? clock.instant().plus(properties.lockDuration())
                    : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
