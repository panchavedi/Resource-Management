package com.talent.server.repository;

import com.talent.server.entity.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    /** All stored skills of the given employees, with the skill eagerly loaded (one query, no N+1). */
    @Query("select es from EmployeeSkill es join fetch es.skill where es.employee.id in :employeeIds")
    List<EmployeeSkill> findByEmployeeIds(@Param("employeeIds") Collection<Long> employeeIds);
}
