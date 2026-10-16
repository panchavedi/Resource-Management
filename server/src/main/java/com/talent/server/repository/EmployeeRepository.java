package com.talent.server.repository;

import com.talent.server.entity.Employee;
import com.talent.server.enums.EmploymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Row lock ({@code SELECT ... FOR UPDATE}) that serialises every capacity-changing operation for one
     * employee. Two concurrent requests for the same person queue here, so the second one sees the first
     * one's committed allocation when it re-checks capacity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    @Query("select distinct e from Employee e left join fetch e.skills s left join fetch s.skill where e.id = :id")
    Optional<Employee> findWithSkillsById(@Param("id") Long id);

    /** Employees holding at least one of the given skills (the candidate pool for a demand). */
    @Query("""
            select distinct e from Employee e join e.skills s
            where s.skill.id in :skillIds
            """)
    List<Employee> findHoldingAnySkill(@Param("skillIds") Collection<Long> skillIds);

    List<Employee> findByEmploymentStatusIn(Collection<EmploymentStatus> statuses);

    @Query("select distinct e.department from Employee e order by e.department")
    List<String> findDistinctDepartments();

    @Query("select distinct e.location from Employee e where e.location is not null order by e.location")
    List<String> findDistinctLocations();
}
