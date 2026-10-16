-- SQL task 2: assignable employees grouped by skill and proficiency level.
SELECT s.category,
       s.name                                                   AS skill,
       es.proficiency_level,
       COUNT(*)                                                 AS employee_count,
       COUNT(*) FILTER (WHERE es.last_validated_at IS NOT NULL) AS validated_count,
       STRING_AGG(e.name, ', ' ORDER BY e.name)                 AS employees
FROM employee_skills es
         JOIN skills s ON s.id = es.skill_id
         JOIN employees e ON e.id = es.employee_id
WHERE e.employment_status IN ('ACTIVE', 'BENCH')
GROUP BY s.category, s.name, es.proficiency_level
ORDER BY s.category, s.name, es.proficiency_level DESC
