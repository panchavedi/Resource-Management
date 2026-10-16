package com.talent.server.repository;

import com.talent.server.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Skill> findByNameIgnoreCase(String name);

    List<Skill> findAllByOrderByCategoryAscNameAsc();

    @Query("select distinct s.category from Skill s order by s.category")
    List<String> findDistinctCategories();
}
