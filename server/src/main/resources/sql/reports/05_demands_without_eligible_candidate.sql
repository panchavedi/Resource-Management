-- SQL task 5: open demands for which no employee passes the hard eligibility rules:
--   assignable status, every mandatory skill at the minimum level, and free capacity on at least one day.
-- Parameter: :maxCapacity (organisation capacity limit).
WITH open_demands AS (SELECT *
                      FROM project_demands
                      WHERE status IN ('OPEN', 'PARTIALLY_FILLED')),
     skill_qualified AS (SELECT d.id AS demand_id, e.id AS employee_id
                         FROM open_demands d
                                  CROSS JOIN employees e
                         WHERE e.employment_status IN ('ACTIVE', 'BENCH')
                           AND NOT EXISTS (SELECT 1
                                           FROM demand_skills ds
                                           WHERE ds.demand_id = d.id
                                             AND ds.mandatory
                                             AND NOT EXISTS (SELECT 1
                                                             FROM employee_skills es
                                                             WHERE es.employee_id = e.id
                                                               AND es.skill_id = ds.skill_id
                                                               AND es.proficiency_level >= ds.minimum_proficiency))),
     eligible AS (SELECT sq.demand_id, sq.employee_id
                  FROM skill_qualified sq
                           JOIN open_demands d ON d.id = sq.demand_id
                           JOIN employees e ON e.id = sq.employee_id
                  WHERE EXISTS (SELECT 1
                                FROM generate_series(d.start_date, d.end_date, INTERVAL '1 day') g(day)
                                WHERE LEAST(e.capacity_percent, :maxCapacity)
                                          - COALESCE((SELECT SUM(a.allocation_percent)
                                                      FROM allocations a
                                                      WHERE a.employee_id = e.id
                                                        AND a.status IN ('PENDING_APPROVAL', 'APPROVED')
                                                        AND CAST(g.day AS date) BETWEEN a.start_date AND a.end_date),
                                                     0) > 0))
SELECT d.id                                                                AS demand_id,
       d.project_name,
       d.role,
       d.priority,
       d.start_date,
       d.end_date,
       (SELECT COUNT(*) FROM skill_qualified sq WHERE sq.demand_id = d.id) AS skill_qualified_count,
       CASE
           WHEN (SELECT COUNT(*) FROM skill_qualified sq WHERE sq.demand_id = d.id) = 0
               THEN 'No assignable employee holds every mandatory skill at the required level'
           ELSE 'Every skill-qualified employee is fully booked for the period'
           END                                                             AS reason
FROM open_demands d
WHERE NOT EXISTS (SELECT 1 FROM eligible el WHERE el.demand_id = d.id)
ORDER BY CASE d.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END, d.start_date
