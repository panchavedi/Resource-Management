package com.talent.server.security;

import com.talent.server.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2027-01-01T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    @Test
    void locksAfterMaxFailuresAndUnlocksAfterDuration() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(
                new SecurityProperties(null, "test", Duration.ofHours(1), 3, Duration.ofMinutes(5), List.of()), clock);

        service.recordFailure("Manager");
        service.recordFailure("manager");
        assertThat(service.isLocked("manager")).isFalse();
        service.recordFailure("MANAGER ");
        assertThat(service.isLocked("manager")).isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertThat(service.isLocked("manager")).isFalse();
    }

    @Test
    void successResetsCounter() {
        LoginAttemptService service = new LoginAttemptService(
                new SecurityProperties(null, "test", Duration.ofHours(1), 2, Duration.ofMinutes(5), List.of()),
                new MutableClock());

        service.recordFailure("approver");
        service.recordSuccess("approver");
        service.recordFailure("approver");
        assertThat(service.isLocked("approver")).isFalse();
    }
}
