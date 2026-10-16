-- SQL task 6b: demanded headcount vs. qualified supply per skill across open demands.
WITH demand_by_skill AS (SELECT ds.skill_id,
                                MIN(ds.minimum_proficiency) AS min_level,
                                COUNT(DISTINCT d.id)        AS open_demands,
                                SUM(d.headcount)            AS demanded_headcount
                         FROM demand_skills ds
                                  JOIN project_demands d ON d.id = ds.demand_id
                         WHERE d.status IN ('OPEN', 'PARTIALLY_FILLED')
                         GROUP BY ds.skill_id)
SELECT s.category,
       s.name                                                            AS skill,
       dbs.open_demands,
       dbs.demanded_headcount,
       COUNT(es.id) FILTER (WHERE es.proficiency_level >= dbs.min_level) AS qualified_supply,
       COUNT(es.id) FILTER (WHERE es.proficiency_level < dbs.min_level)  AS near_miss_supply,
       GREATEST(dbs.demanded_headcount
                    - COUNT(es.id) FILTER (WHERE es.proficiency_level >= dbs.min_level), 0) AS people_short
FROM demand_by_skill dbs
         JOIN skills s ON s.id = dbs.skill_id
         LEFT JOIN employee_skills es
                   ON es.skill_id = dbs.skill_id
                       AND es.employee_id IN (SELECT id FROM employees WHERE employment_status IN ('ACTIVE', 'BENCH'))
GROUP BY s.category, s.name, dbs.open_demands, dbs.demanded_headcount, dbs.min_level
ORDER BY people_short DESC, s.category, s.name
