package com.talent.server.api;

import com.talent.server.dto.request.CreateAllocationRequest;
import com.talent.server.dto.response.AllocationResponse;
import com.talent.server.dto.response.DemandResponse;
import com.talent.server.entity.Employee;
import com.talent.server.entity.Skill;
import com.talent.server.exception.ApiException;
import com.talent.server.exception.ErrorCode;
import com.talent.server.service.AllocationService;
import com.talent.server.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.talent.server.support.TestData.TODAY;
import static com.talent.server.support.TestData.level;
import static com.talent.server.support.TestData.requires;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-06-11: concurrent requests for the same employee are serialised by the
 * employee row lock, so
 * the capacity check always sees the other request's committed result.
 */
class ConcurrentAllocationIT extends IntegrationTest {

    private static final LocalDate START = TODAY.plusDays(10);
    private static final LocalDate END = TODAY.plusDays(40);

    @Autowired
    AllocationService allocationService;

    private sealed interface Outcome permits Success, Failure {
    }

    private record Success(AllocationResponse allocation) implements Outcome {
    }

    private record Failure(ErrorCode code) implements Outcome {
    }

    private List<Outcome> runConcurrently(List<Callable<AllocationResponse>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<AllocationResponse> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return new Success(task.call());
                    } catch (ApiException e) {
                        return new Failure(e.getCode());
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private Callable<AllocationResponse> proposal(Long employeeId, Long demandId, int percent) {
        return () -> allocationService
                .propose(new CreateAllocationRequest(employeeId, demandId, START, END, percent, null),
                        null, "manager")
                .allocation();
    }

    @RepeatedTest(3)
    @DisplayName("TC-06-11: two concurrent 100% requests for one employee -> exactly one persists")
    void twoConflictingRequests() throws Exception {
        Skill java = data.skill("Java", "Backend");
        Employee asha = data.employee("E1", "Asha Rao", "Pune", level(java, 5));
        DemandResponse atlas = data.demand("Atlas", START, END, 100, requires(java, 4));
        DemandResponse nimbus = data.demand("Nimbus", START, END, 100, requires(java, 4));

        List<Outcome> outcomes = runConcurrently(List.of(
                proposal(asha.getId(), atlas.id(), 100),
                proposal(asha.getId(), nimbus.id(), 100)));

        assertThat(outcomes).filteredOn(Success.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(Failure.class::isInstance)
                .singleElement().extracting(o -> ((Failure) o).code()).isEqualTo(ErrorCode.CAPACITY_EXCEEDED);
        assertNoDayOverbooked(asha.getId());
    }

    @Test
    @DisplayName("TC-06-11: ten concurrent 30% requests -> at most three persist and no day exceeds 100%")
    void manyConcurrentRequests() throws Exception {
        Skill java = data.skill("Java", "Backend");
        Employee asha = data.employee("E1", "Asha Rao", "Pune", level(java, 5));
        List<Callable<AllocationResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DemandResponse d = data.demand("Project " + i, START, END, 30, requires(java, 4));
            tasks.add(proposal(asha.getId(), d.id(), 30));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).filteredOn(Success.class::isInstance).hasSize(3);
        assertThat(outcomes).filteredOn(Failure.class::isInstance)
                .allSatisfy(o -> assertThat(((Failure) o).code()).isEqualTo(ErrorCode.CAPACITY_EXCEEDED));
        assertNoDayOverbooked(asha.getId());
    }

    @Test
    @DisplayName("TC-06-11: concurrent approval and cancellation of the same allocation leave one consistent outcome")
    void approveVersusCancel() throws Exception {
        Skill java = data.skill("Java", "Backend");
        Employee asha = data.employee("E1", "Asha Rao", "Pune", level(java, 5));
        DemandResponse atlas = data.demand("Atlas", START, END, 100, requires(java, 4));
        AllocationResponse pending = data.propose(asha.getId(), atlas.id(), START, END, 100);

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> allocationService.approve(pending.id(), null, "approver"),
                () -> allocationService.cancel(pending.id(), "withdrawn", "manager")));

        String finalStatus = jdbc.queryForObject("select status from allocations where id = ?", String.class,
                pending.id());
        long events = jdbc.queryForObject("select count(*) from allocation_events where allocation_id = ?", Long.class,
                pending.id());
        if (finalStatus.equals("CANCELLED")) {
            // Either cancel won outright (approval then failed), or approval ran first and
            // was cancelled afterwards.
            assertThat(events).isIn(2L, 3L);
        } else {
            assertThat(finalStatus).isEqualTo("APPROVED");
            assertThat(outcomes).filteredOn(Failure.class::isInstance).singleElement()
                    .extracting(o -> ((Failure) o).code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        }
        assertNoDayOverbooked(asha.getId());
    }

    /**
     * Database-level check: on every day, capacity-holding allocations sum to at
     * most 100%.
     */
    private void assertNoDayOverbooked(Long employeeId) {
        Integer peak = jdbc.queryForObject("""
                SELECT COALESCE(MAX(load), 0) FROM (
                    SELECT g.day, SUM(a.allocation_percent) AS load
                    FROM generate_series(CAST(? AS date), CAST(? AS date), INTERVAL '1 day') g(day)
                    JOIN allocations a ON a.employee_id = ? AND a.status IN ('PENDING_APPROVAL', 'APPROVED')
                                       AND CAST(g.day AS date) BETWEEN a.start_date AND a.end_date
                    GROUP BY g.day) per_day
                """, Integer.class, START, END, employeeId);
        assertThat(peak).isLessThanOrEqualTo(100);
    }
}
