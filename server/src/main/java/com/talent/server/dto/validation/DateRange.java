package com.talent.server.dto.validation;

import java.time.LocalDate;

/** Implemented by request DTOs that carry a date range validated by {@link ValidDateRange}. */
public interface DateRange {

    LocalDate startDate();

    LocalDate endDate();
}
