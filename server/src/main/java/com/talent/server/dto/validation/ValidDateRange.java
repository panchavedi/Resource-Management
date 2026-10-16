package com.talent.server.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint: {@code endDate} must be on or after {@code startDate}. The violation is
 * reported on the {@code endDate} field so clients can highlight it. Null dates are left to
 * {@code @NotNull} on the fields themselves.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "must be on or after startDate";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
