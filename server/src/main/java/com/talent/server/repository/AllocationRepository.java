package com.talent.server.repository;

import com.talent.server.entity.Allocation;
import com.talent.server.enums.AllocationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long>, JpaSpecificationExecutor<Allocation> {

    /** Allocations of one employee in the given statuses whose date range intersects [start, end]. */
    @Query("""
            select a from Allocation a join fetch a.demand
            where a.employee.id = :employeeId
              and a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
            """)
    List<Allocation> findOverlapping(@Param("employeeId") Long employeeId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end,
                                     @Param("statuses") Collection<AllocationStatus> statuses);

    /** Batch variant used when evaluating many candidates at once. */
    @Query("""
            select a from Allocation a join fetch a.demand
            where a.employee.id in :employeeIds
              and a.status in :statuses
              and a.startDate <= :end and a.endDate >= :start
            """)
    List<Allocation> findOverlappingForEmployees(@Param("employeeIds") Collection<Long> employeeIds,
                                                 @Param("start") LocalDate start,
                                                 @Param("end") LocalDate end,
                                                 @Param("statuses") Collection<AllocationStatus> statuses);

    /** Ids needed to take locks in a fixed order (demand, then employee) before loading the allocation. */
    interface LockKeys {
        Long getDemandId();

        Long getEmployeeId();
    }

    @Query("select a.demand.id as demandId, a.employee.id as employeeId from Allocation a where a.id = :id")
    Optional<LockKeys> findLockKeys(@Param("id") Long id);

    interface DemandStatusCount {
        Long getDemandId();

        AllocationStatus getStatus();

        long getTotal();
    }

    @Query("""
            select a.demand.id as demandId, a.status as status, count(a) as total
            from Allocation a
            where a.demand.id in :demandIds and a.status in :statuses
            group by a.demand.id, a.status
            """)
    List<DemandStatusCount> countByDemandAndStatus(@Param("demandIds") Collection<Long> demandIds,
                                                   @Param("statuses") Collection<AllocationStatus> statuses);

    @EntityGraph(attributePaths = {"employee", "demand"})
    List<Allocation> findByDemandIdAndStatusIn(Long demandId, Collection<AllocationStatus> statuses);

    long countByDemandIdAndStatus(Long demandId, AllocationStatus status);

    long countByDemandIdAndStatusIn(Long demandId, Collection<AllocationStatus> statuses);

    boolean existsByEmployeeIdAndDemandIdAndStatusIn(Long employeeId, Long demandId,
                                                     Collection<AllocationStatus> statuses);

    Optional<Allocation> findByIdempotencyKey(String idempotencyKey);

    @Query("select a from Allocation a left join fetch a.events where a.id = :id")
    Optional<Allocation> findWithEventsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"employee", "demand"})
    List<Allocation> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    @Override
    @EntityGraph(attributePaths = {"employee", "demand"})
    Page<Allocation> findAll(Specification<Allocation> spec, Pageable pageable);
}
