package com.talent.server.service.matching;

import com.talent.server.enums.AllocationStatus;
import com.talent.server.enums.AvailabilityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityCalculatorTest {

    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final LocalDate END = LocalDate.of(2027, 1, 31);

    private static Booking booking(LocalDate start, LocalDate end, int percent, AllocationStatus status) {
        return new Booking(1L, 1L, "P", start, end, percent, status);
    }

    @Test
    @DisplayName("No bookings: fully available for every day")
    void noBookings() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 100, 100, List.of());

        assertThat(p.status()).isEqualTo(AvailabilityStatus.FULL);
        assertThat(p.totalDays()).isEqualTo(31);
        assertThat(p.availableDays()).isEqualTo(31);
        assertThat(p.coveragePercent()).isEqualTo(100.0);
        assertThat(p.windows()).hasSize(1);
    }

    @Test
    @DisplayName("TC-06-05 (unit): booked at 100% for the whole period -> NONE")
    void fullyBooked() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 100, 100,
                List.of(booking(START.minusDays(10), END.plusDays(10), 100, AllocationStatus.APPROVED)));

        assertThat(p.status()).isEqualTo(AvailabilityStatus.NONE);
        assertThat(p.availableDays()).isZero();
        assertThat(p.peakLoadPercent()).isEqualTo(100);
        assertThat(p.minFreePercent()).isZero();
        assertThat(p.coveragePercent()).isZero();
    }

    @Test
    @DisplayName("TC-06-06 (unit): booked for the second half -> PARTIAL with exact day counts and windows")
    void partiallyBooked() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 100, 100,
                List.of(booking(LocalDate.of(2027, 1, 16), END, 100, AllocationStatus.APPROVED)));

        assertThat(p.status()).isEqualTo(AvailabilityStatus.PARTIAL);
        assertThat(p.availableDays()).isEqualTo(15);
        assertThat(p.coveragePercent()).isEqualTo(48.4);
        assertThat(p.windows()).extracting(AvailabilityProfile.AvailabilityWindow::startDate,
                        AvailabilityProfile.AvailabilityWindow::endDate, AvailabilityProfile.AvailabilityWindow::freePercent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(START, LocalDate.of(2027, 1, 15), 100),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2027, 1, 16), END, 0));
    }

    @Test
    @DisplayName("Overlap is evaluated per day: non-overlapping 60% bookings never add up")
    void perDayNotSummed() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 100, 40, List.of(
                booking(START, LocalDate.of(2027, 1, 10), 60, AllocationStatus.APPROVED),
                booking(LocalDate.of(2027, 1, 20), END, 60, AllocationStatus.PENDING_APPROVAL)));

        assertThat(p.peakLoadPercent()).isEqualTo(60);
        assertThat(p.status()).isEqualTo(AvailabilityStatus.FULL);
    }

    @Test
    @DisplayName("Concurrent bookings on the same days add up; pending holds capacity, confirmed load is separate")
    void overlappingBookingsAddUp() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 100, 50, List.of(
                booking(START, END, 30, AllocationStatus.APPROVED),
                booking(LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 20), 40, AllocationStatus.PENDING_APPROVAL)));

        assertThat(p.peakLoadPercent()).isEqualTo(70);
        assertThat(p.status()).isEqualTo(AvailabilityStatus.PARTIAL);
        assertThat(p.availableDays()).isEqualTo(21);
        AvailabilityProfile.AvailabilityWindow middle = p.windows().get(1);
        assertThat(middle.loadPercent()).isEqualTo(70);
        assertThat(middle.confirmedLoadPercent()).isEqualTo(30);
    }

    @Test
    @DisplayName("Cancelled and rejected allocations release capacity")
    void releasedStatusesIgnored() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 100, 100, List.of(
                booking(START, END, 100, AllocationStatus.CANCELLED),
                booking(START, END, 100, AllocationStatus.REJECTED)));

        assertThat(p.status()).isEqualTo(AvailabilityStatus.FULL);
        assertThat(p.bookings()).isEmpty();
    }

    @Test
    @DisplayName("Part-time capacity caps availability")
    void partTimeCapacity() {
        AvailabilityProfile p = AvailabilityCalculator.calculate(START, END, 50, 60, List.of());

        assertThat(p.status()).isEqualTo(AvailabilityStatus.PARTIAL);
        assertThat(p.minFreePercent()).isEqualTo(50);
        assertThat(p.coveragePercent()).isEqualTo(83.3);
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> AvailabilityCalculator.calculate(END, START, 100, 100, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
