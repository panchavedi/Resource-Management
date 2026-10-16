package com.talent.server.repository;

import com.talent.server.entity.ProjectDemand;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DemandRepository extends JpaRepository<ProjectDemand, Long>, JpaSpecificationExecutor<ProjectDemand> {

    /** Serialises headcount and status changes for one demand. Always acquired before the employee lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ProjectDemand d where d.id = :id")
    Optional<ProjectDemand> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select distinct d from ProjectDemand d
            left join fetch d.requiredSkills rs left join fetch rs.skill
            where d.id = :id
            """)
    Optional<ProjectDemand> findWithSkillsById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"requiredSkills", "requiredSkills.skill"})
    Page<ProjectDemand> findAll(Specification<ProjectDemand> spec, Pageable pageable);
}
