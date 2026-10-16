-- SQL task 6a: skill gaps of open demands grouped by skill category.
-- A requirement is uncovered when fewer assignable employees meet the minimum level than the headcount.
WITH reqs AS (SELECT s.category,
                     s.name AS skill,
                     d.headcount,
                     (SELECT COUNT(*)
                      FROM employee_skills es
                               JOIN employees e ON e.id = es.employee_id
                      WHERE es.skill_id = ds.skill_id
                        AND es.proficiency_level >= ds.minimum_proficiency
                        AND e.employment_status IN ('ACTIVE', 'BENCH')) AS qualified_supply,
                     (SELECT COUNT(*)
                      FROM employee_skills es
                               JOIN employees e ON e.id = es.employee_id
                      WHERE es.skill_id = ds.skill_id
                        AND es.proficiency_level < ds.minimum_proficiency
                        AND e.employment_status IN ('ACTIVE', 'BENCH')) AS near_miss
              FROM demand_skills ds
                       JOIN skills s ON s.id = ds.skill_id
                       JOIN project_demands d ON d.id = ds.demand_id
              WHERE d.status IN ('OPEN', 'PARTIALLY_FILLED'))
SELECT r.category,
       COUNT(*)                                                                           AS requirement_count,
       COUNT(*) FILTER (WHERE r.qualified_supply < r.headcount)                           AS uncovered_requirements,
       SUM(GREATEST(r.headcount - r.qualified_supply, 0))                                 AS people_short,
       SUM(r.near_miss)                                                                   AS upskill_candidates,
       STRING_AGG(DISTINCT r.skill, ', ') FILTER (WHERE r.qualified_supply < r.headcount) AS skills_short,
       (SELECT COUNT(*)
        FROM training_recommendations tr
                 JOIN skills s2 ON s2.id = tr.skill_id
        WHERE s2.category = r.category
          AND tr.status IN ('RECOMMENDED', 'IN_PROGRESS'))                                AS open_training_recommendations
FROM reqs r
GROUP BY r.category
ORDER BY people_short DESC, uncovered_requirements DESC, r.category
