-- SQL task 3: assignable employees with enough free capacity on at least one day of [:from, :to].
-- Load is evaluated per day (pending + approved allocations), matching the Java availability rules.
-- Parameters: :from, :to (dates), :requested (percent), :maxCapacity (organisation limit).
WITH days AS (SELECT CAST(g AS date) AS day
              FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') g),
     emp AS (SELECT id, employee_code, name, department, location,
                    LEAST(capacity_percent, :maxCapacity) AS capacity
             FROM employees
             WHERE employment_status IN ('ACTIVE', 'BENCH')),
     daily AS (SELECT e.id AS employee_id, days.day, COALESCE(SUM(a.allocation_percent), 0) AS load
               FROM emp e
                        CROSS JOIN days
                        LEFT JOIN allocations a
                                  ON a.employee_id = e.id
                                      AND a.status IN ('PENDING_APPROVAL', 'APPROVED')
                                      AND days.day BETWEEN a.start_date AND a.end_date
               GROUP BY e.id, days.day)
SELECT e.id                                                          AS employee_id,
       e.employee_code,
       e.name,
       e.department,
       e.location,
       e.capacity                                                    AS capacity_percent,
       MAX(daily.load)                                               AS peak_load_percent,
       GREATEST(e.capacity - MAX(daily.load), 0)                     AS min_free_percent,
       COUNT(*) FILTER (WHERE e.capacity - daily.load >= :requested) AS available_days,
       COUNT(*)                                                      AS total_days
FROM emp e
         JOIN daily ON daily.employee_id = e.id
GROUP BY e.id, e.employee_code, e.name, e.department, e.location, e.capacity
HAVING COUNT(*) FILTER (WHERE e.capacity - daily.load >= :requested) > 0
ORDER BY available_days DESC, min_free_percent DESC, e.name
