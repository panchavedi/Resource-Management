package com.talent.server.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talent.server.enums.MandatorySkillPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables of the deterministic candidate matching. Weights must sum to 1.0
 * (checked at startup).
 *
 * @param mandatorySkillPolicy    EXCLUDE or FLAG candidates failing a mandatory
 *                                skill
 * @param evidenceFreshnessMonths a validation older than this is reported as
 *                                STALE
 * @param weights                 factor weights of the total score
 */
@Validated
@ConfigurationProperties("app.matching")
public record MatchingProperties(
        @NotNull @DefaultValue("EXCLUDE") MandatorySkillPolicy mandatorySkillPolicy,
        @Min(1) @DefaultValue("18") int evidenceFreshnessMonths,
        @Valid @NotNull @DefaultValue Weights weights) {

    public record Weights(
            @DecimalMin("0") @DecimalMax("1") @DefaultValue("0.50") double skill,
            @DecimalMin("0") @DecimalMax("1") @DefaultValue("0.30") double availability,
            @DecimalMin("0") @DecimalMax("1") @DefaultValue("0.15") double location,
            @DecimalMin("0") @DecimalMax("1") @DefaultValue("0.05") double evidence) {

        @JsonIgnore
        @AssertTrue(message = "app.matching.weights must sum to 1.0")
        public boolean isNormalised() {
            return Math.abs(skill + availability + location + evidence - 1.0) < 0.0001;
        }
    }
}
