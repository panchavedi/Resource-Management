package com.talent.server.service.matching;

import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.AvailabilityStatus;
import com.talent.server.service.matching.AvailabilityProfile.AvailabilityWindow;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure availability arithmetic (no I/O, fully unit-tested).
 * <p>
 * Overlap is evaluated per day, not by summing every overlapping allocation:
 * two 50% allocations in
 * January and March do not make someone 100% busy in a January–March request.
 * The range is split at
 * every booking boundary ("sweep line"), so cost is O(b log b) in the number of
 * bookings, independent
 * of the length of the range.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    public static AvailabilityProfile calculate(LocalDate start, LocalDate end, int capacityPercent,
            int requestedPercent, Collection<Booking> bookings) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
        List<Booking> relevant = bookings.stream()
                .filter(b -> b.status().holdsCapacity() && b.overlaps(start, end))
                .toList();

        TreeSet<LocalDate> cuts = new TreeSet<>();
        cuts.add(start);
        cuts.add(end.plusDays(1));
        for (Booking b : relevant) {
            cuts.add(max(b.startDate(), start));
            cuts.add(min(b.endDate(), end).plusDays(1));
        }

        List<AvailabilityWindow> windows = new ArrayList<>();
        LocalDate from = null;
        for (LocalDate cut : cuts) {
            if (from != null) {
                append(windows, window(from, cut.minusDays(1), capacityPercent, requestedPercent, relevant));
            }
            from = cut;
        }

        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long availableDays = windows.stream().filter(AvailabilityWindow::meetsRequest)
                .mapToLong(AvailabilityWindow::days).sum();
        int peakLoad = windows.stream().mapToInt(AvailabilityWindow::loadPercent).max().orElse(0);
        int minFree = windows.stream().mapToInt(AvailabilityWindow::freePercent).min().orElse(capacityPercent);
        double weightedCoverage = windows.stream()
                .mapToDouble(w -> w.days() * Math.min(1.0, (double) w.freePercent() / requestedPercent))
                .sum();
        double coveragePercent = round1(weightedCoverage / totalDays * 100.0);

        AvailabilityStatus status;
        if (availableDays == totalDays) {
            status = AvailabilityStatus.FULL;
        } else if (windows.stream().allMatch(w -> w.freePercent() == 0)) {
            status = AvailabilityStatus.NONE;
        } else {
            status = AvailabilityStatus.PARTIAL;
        }
        return new AvailabilityProfile(start, end, capacityPercent, requestedPercent, status, peakLoad, minFree,
                totalDays, availableDays, coveragePercent, List.copyOf(windows), relevant);
    }

    /** Highest committed load on any single day of [start, end]. */
    public static int peakLoad(LocalDate start, LocalDate end, Collection<Booking> bookings) {
        return calculate(start, end, 100, 1, bookings).peakLoadPercent();
    }

    private static AvailabilityWindow window(LocalDate from, LocalDate to, int capacity, int requested,
            List<Booking> bookings) {
        int load = 0;
        int confirmed = 0;
        for (Booking b : bookings) {
            if (b.covers(from)) {
                load += b.allocationPercent();
                if (b.status() == AllocationStatus.APPROVED) {
                    confirmed += b.allocationPercent();
                }
            }
        }
        int free = Math.max(0, capacity - load);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new AvailabilityWindow(from, to, days, load, confirmed, free, free >= requested);
    }

    /** Merges adjacent windows with identical load so the output stays compact. */
    private static void append(List<AvailabilityWindow> windows, AvailabilityWindow next) {
        if (!windows.isEmpty()) {
            AvailabilityWindow last = windows.getLast();
            if (last.loadPercent() == next.loadPercent() && last.confirmedLoadPercent() == next.confirmedLoadPercent()
                    && last.endDate().plusDays(1).equals(next.startDate())) {
                windows.set(windows.size() - 1, new AvailabilityWindow(last.startDate(), next.endDate(),
                        last.days() + next.days(), last.loadPercent(), last.confirmedLoadPercent(),
                        last.freePercent(), last.meetsRequest()));
                return;
            }
        }
        windows.add(next);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
