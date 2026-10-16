package com.talent.server.repository;

import com.talent.server.entity.LearningResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

    @Query("""
            select lr from LearningResource lr join fetch lr.skill
            where lr.skill.id in :skillIds
            order by lr.targetLevel asc, lr.durationHours asc
            """)
    List<LearningResource> findBySkillIds(@Param("skillIds") Collection<Long> skillIds);
}
