package com.talent.server.service.matching;

import com.talent.server.config.MatchingProperties;
import com.talent.server.enums.EvidenceStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Classifies a stored proficiency by how recently it was validated. */
@Component
public class EvidenceClassifier {

    private final int freshnessMonths;
    private final Clock clock;

    public EvidenceClassifier(MatchingProperties properties, Clock clock) {
        this.freshnessMonths = properties.evidenceFreshnessMonths();
        this.clock = clock;
    }

    public EvidenceStatus classify(LocalDate lastValidatedAt) {
        if (lastValidatedAt == null) {
            return EvidenceStatus.UNVALIDATED;
        }
        return lastValidatedAt.isBefore(LocalDate.now(clock).minusMonths(freshnessMonths))
                ? EvidenceStatus.STALE
                : EvidenceStatus.VALIDATED;
    }

    public int freshnessMonths() {
        return freshnessMonths;
    }
}
