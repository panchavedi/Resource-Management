-- SQL task 4b: capacity-weighted utilisation per department for [:from, :to].
WITH params AS (SELECT CAST(:from AS date) AS f, CAST(:to AS date) AS t),
     overlap AS (SELECT a.employee_id,
                        a.status,
                        a.allocation_percent,
                        GREATEST(0, LEAST(a.end_date, p.t) - GREATEST(a.start_date, p.f) + 1) AS days
                 FROM allocations a
                          CROSS JOIN params p
                 WHERE a.status IN ('APPROVED', 'PENDING_APPROVAL')
                   AND a.start_date <= p.t
                   AND a.end_date >= p.f),
     per_employee AS (SELECT e.department,
                             (p.t - p.f + 1) * e.capacity_percent AS capacity_pd,
                             COALESCE(SUM(o.days * o.allocation_percent)
                                      FILTER (WHERE o.status = 'APPROVED'), 0)         AS approved_pd,
                             COALESCE(SUM(o.days * o.allocation_percent)
                                      FILTER (WHERE o.status = 'PENDING_APPROVAL'), 0) AS pending_pd
                      FROM employees e
                               CROSS JOIN params p
                               LEFT JOIN overlap o ON o.employee_id = e.id
                      WHERE e.employment_status <> 'INACTIVE'
                      GROUP BY e.id, e.department, e.capacity_percent, p.f, p.t)
SELECT department,
       COUNT(*)                                                                AS employee_count,
       ROUND(SUM(approved_pd)::numeric / NULLIF(SUM(capacity_pd), 0) * 100, 1) AS utilization_percent,
       ROUND(SUM(pending_pd)::numeric / NULLIF(SUM(capacity_pd), 0) * 100, 1)  AS tentative_percent
FROM per_employee
GROUP BY department
ORDER BY utilization_percent DESC NULLS LAST, department
