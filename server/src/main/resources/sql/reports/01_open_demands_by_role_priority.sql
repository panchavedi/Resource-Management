-- SQL task 1: open demands grouped by role and priority (most urgent first).
-- Open positions = headcount minus approved allocations.
SELECT d.role,
       d.priority,
       COUNT(*)                                        AS demand_count,
       SUM(d.headcount)                                AS total_headcount,
       COALESCE(SUM(a.approved), 0)                    AS approved_count,
       SUM(d.headcount) - COALESCE(SUM(a.approved), 0) AS open_positions,
       MIN(d.start_date)                               AS earliest_start
FROM project_demands d
         LEFT JOIN (SELECT demand_id, COUNT(*) AS approved
                    FROM allocations
                    WHERE status = 'APPROVED'
                    GROUP BY demand_id) a ON a.demand_id = d.id
WHERE d.status IN ('OPEN', 'PARTIALLY_FILLED')
GROUP BY d.role, d.priority
ORDER BY CASE d.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END,
         d.role
