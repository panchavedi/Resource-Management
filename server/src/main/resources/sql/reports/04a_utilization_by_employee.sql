-- SQL task 4a: utilisation per employee for [:from, :to].
-- utilisation = approved person-days / capacity person-days; tentative = the same for pending allocations.
WITH params AS (SELECT CAST(:from AS date) AS f, CAST(:to AS date) AS t),
     overlap AS (SELECT a.employee_id,
                        a.status,
                        a.allocation_percent,
                        GREATEST(0, LEAST(a.end_date, p.t) - GREATEST(a.start_date, p.f) + 1) AS days
                 FROM allocations a
                          CROSS JOIN params p
                 WHERE a.status IN ('APPROVED', 'PENDING_APPROVAL')
                   AND a.start_date <= p.t
                   AND a.end_date >= p.f)
SELECT e.id AS employee_id,
       e.employee_code,
       e.name,
       e.department,
       e.capacity_percent,
       ROUND(COALESCE(SUM(o.days * o.allocation_percent) FILTER (WHERE o.status = 'APPROVED'), 0)::numeric
                 / ((p.t - p.f + 1) * e.capacity_percent) * 100, 1) AS utilization_percent,
       ROUND(COALESCE(SUM(o.days * o.allocation_percent) FILTER (WHERE o.status = 'PENDING_APPROVAL'), 0)::numeric
                 / ((p.t - p.f + 1) * e.capacity_percent) * 100, 1) AS tentative_percent
FROM employees e
         CROSS JOIN params p
         LEFT JOIN overlap o ON o.employee_id = e.id
WHERE e.employment_status <> 'INACTIVE'
GROUP BY e.id, e.employee_code, e.name, e.department, e.capacity_percent, p.f, p.t
ORDER BY e.department, utilization_percent DESC, e.name
