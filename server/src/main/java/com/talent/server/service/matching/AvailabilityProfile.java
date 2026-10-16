package com.talent.server.service.matching;

import com.talent.server.enums.AvailabilityStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Day-accurate availability of one employee for a requested range and
 * percentage.
 *
 * @param capacityPercent effective capacity (employee capacity capped by the
 *                        organisation limit)
 * @param peakLoadPercent highest committed load (pending + approved) on any day
 *                        of the range
 * @param minFreePercent  lowest free capacity on any day of the range
 * @param availableDays   days on which the requested percentage fits
 * @param coveragePercent average of min(1, free / requested) across all days,
 *                        as 0–100
 * @param windows         contiguous periods with constant load, in date order
 * @param bookings        the capacity-holding allocations that overlap the
 *                        range
 */
public record AvailabilityProfile(
                LocalDate startDate,
                LocalDate endDate,
                int capacityPercent,
                int requestedPercent,
                AvailabilityStatus status,
                int peakLoadPercent,
                int minFreePercent,
                long totalDays,
                long availableDays,
                double coveragePercent,
                List<AvailabilityWindow> windows,
                List<Booking> bookings) {

        /**
         * @param loadPercent          committed load (pending + approved)
         * @param confirmedLoadPercent approved load only
         */
        public record AvailabilityWindow(LocalDate startDate, LocalDate endDate, long days, int loadPercent,
                        int confirmedLoadPercent, int freePercent, boolean meetsRequest) {
        }

        /** Human-readable, data-derived description, reused by the UI and the agent. */
        public String describe() {
                return switch (status) {
                        case FULL -> "Available at %d%% for all %d days (%s to %s); peak existing load %d%%."
                                        .formatted(requestedPercent, totalDays, startDate, endDate, peakLoadPercent);
                        case PARTIAL ->
                                "Partially available: %d of %d days can take %d%% (coverage %s%%); minimum free capacity %d%%."
                                                .formatted(availableDays, totalDays, requestedPercent, coveragePercent,
                                                                minFreePercent);
                        case NONE -> "Not available: no free capacity on any day between %s and %s (load %d%% of %d%%)."
                                        .formatted(startDate, endDate, peakLoadPercent, capacityPercent);
                };
        }
}
