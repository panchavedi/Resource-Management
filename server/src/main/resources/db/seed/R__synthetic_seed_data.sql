-- =====================================================================================
-- Synthetic demo data (fictional people and projects only).
-- Runs only against an empty database; dates are relative to CURRENT_DATE so the demo
-- always has upcoming demands. Excluded from tests and removable via FLYWAY_LOCATIONS.
-- =====================================================================================
DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM employees) THEN
        RETURN;
    END IF;

    -- ------------------------------------------------------------ skills
    INSERT INTO skills (name, category, description)
    VALUES ('Java', 'Backend', 'Java SE 17+ application development'),
           ('Spring Boot', 'Backend', 'Spring Boot services, Spring Data, Spring Security'),
           ('Node.js', 'Backend', 'Node.js services and tooling'),
           ('Go', 'Backend', 'Go services and CLIs'),
           ('Rust', 'Backend', 'Systems programming in Rust'),
           ('Python', 'Backend', 'Python for services and data work'),
           ('React', 'Frontend', 'React component architecture and state management'),
           ('TypeScript', 'Frontend', 'Typed JavaScript for large codebases'),
           ('Next.js', 'Frontend', 'Next.js App Router, SSR and RSC'),
           ('PostgreSQL', 'Data', 'Relational modelling, indexing, query tuning'),
           ('SQL', 'Data', 'Analytical SQL'),
           ('Kafka', 'Data', 'Event streaming with Apache Kafka'),
           ('Spark', 'Data', 'Batch and streaming processing with Apache Spark'),
           ('AWS', 'Cloud & DevOps', 'Core AWS services and architecture'),
           ('Kubernetes', 'Cloud & DevOps', 'Container orchestration'),
           ('Docker', 'Cloud & DevOps', 'Container images and runtime'),
           ('Terraform', 'Cloud & DevOps', 'Infrastructure as code'),
           ('Machine Learning', 'AI & ML', 'Model training, evaluation and deployment'),
           ('LLM Engineering', 'AI & ML', 'Prompting, RAG, evaluation and guardrails'),
           ('Test Automation', 'Quality', 'Automated test strategy and frameworks'),
           ('Playwright', 'Quality', 'End-to-end browser testing'),
           ('UX Design', 'Design', 'Interaction and visual design'),
           ('Business Analysis', 'Delivery', 'Requirements, process and stakeholder analysis'),
           ('Scrum', 'Delivery', 'Agile delivery and facilitation');

    -- ------------------------------------------------------------ employees
    INSERT INTO employees (employee_code, name, email, job_title, department, location, remote_eligible,
                           employment_status, capacity_percent)
    VALUES ('E1001', 'Aarav Kulkarni', 'aarav.kulkarni@example.com', 'Senior Java Engineer', 'Engineering', 'Bengaluru', TRUE, 'ACTIVE', 100),
           ('E1002', 'Meera Iyer', 'meera.iyer@example.com', 'Full-Stack Engineer', 'Engineering', 'Bengaluru', FALSE, 'ACTIVE', 100),
           ('E1003', 'Daniel Brooks', 'daniel.brooks@example.com', 'Staff Backend Engineer', 'Engineering', 'Pune', TRUE, 'ACTIVE', 100),
           ('E1004', 'Sofia Martins', 'sofia.martins@example.com', 'Frontend Engineer', 'Engineering', 'Hyderabad', TRUE, 'BENCH', 100),
           ('E1005', 'Kabir Shah', 'kabir.shah@example.com', 'Data Engineer', 'Data & AI', 'Pune', FALSE, 'ACTIVE', 100),
           ('E1006', 'Priya Nair', 'priya.nair@example.com', 'ML Engineer', 'Data & AI', 'Bengaluru', TRUE, 'ACTIVE', 100),
           ('E1007', 'Liam Carter', 'liam.carter@example.com', 'Cloud Engineer', 'Cloud Platform', 'Chennai', TRUE, 'ACTIVE', 100),
           ('E1008', 'Ananya Reddy', 'ananya.reddy@example.com', 'DevOps Engineer', 'Cloud Platform', 'Hyderabad', FALSE, 'ACTIVE', 100),
           ('E1009', 'Noah Fernandes', 'noah.fernandes@example.com', 'QA Automation Engineer', 'Quality Engineering', 'Pune', TRUE, 'ACTIVE', 100),
           ('E1010', 'Isha Verma', 'isha.verma@example.com', 'Product Designer', 'Design', 'Bengaluru', TRUE, 'ACTIVE', 80),
           ('E1011', 'Rohan Gupta', 'rohan.gupta@example.com', 'Business Analyst', 'Delivery', 'Chennai', FALSE, 'ACTIVE', 100),
           ('E1012', 'Elena Petrova', 'elena.petrova@example.com', 'Scrum Master', 'Delivery', NULL, TRUE, 'ACTIVE', 50),
           ('E1013', 'Vikram Singh', 'vikram.singh@example.com', 'Java Engineer', 'Engineering', 'Hyderabad', FALSE, 'ON_LEAVE', 100),
           ('E1014', 'Zara Khan', 'zara.khan@example.com', 'Data Analyst', 'Data & AI', 'Bengaluru', FALSE, 'BENCH', 100),
           ('E1015', 'Arjun Pillai', 'arjun.pillai@example.com', 'Platform Engineer', 'Cloud Platform', 'Bengaluru', TRUE, 'ACTIVE', 100),
           ('E1016', 'Hannah Lee', 'hannah.lee@example.com', 'Frontend Lead', 'Engineering', 'Pune', TRUE, 'ACTIVE', 100),
           ('E1017', 'Siddharth Joshi', 'siddharth.joshi@example.com', 'Backend Engineer', 'Engineering', 'Bengaluru', TRUE, 'BENCH', 100),
           ('E1018', 'Kavya Das', 'kavya.das@example.com', 'QA Engineer', 'Quality Engineering', 'Chennai', FALSE, 'ACTIVE', 100),
           ('E1019', 'Omar Haddad', 'omar.haddad@example.com', 'Solutions Architect', 'Engineering', 'Hyderabad', TRUE, 'ACTIVE', 100),
           ('E1020', 'Neha Bansal', 'neha.bansal@example.com', 'Data Scientist', 'Data & AI', 'Pune', TRUE, 'ACTIVE', 100),
           ('E1021', 'Lucas Moreau', 'lucas.moreau@example.com', 'Cloud Security Engineer', 'Cloud Platform', 'Bengaluru', FALSE, 'INACTIVE', 100),
           ('E1022', 'Divya Krishnan', 'divya.krishnan@example.com', 'Java Engineer', 'Engineering', 'Bengaluru', FALSE, 'ACTIVE', 100);

    -- ------------------------------------------------------------ employee skills
    -- validated_days_ago NULL = never validated (self-declared); > 540 days = stale evidence.
    INSERT INTO employee_skills (employee_id, skill_id, proficiency_level, evidence, evidence_source,
                                 last_validated_at, validated_by)
    SELECT e.id, s.id, v.lvl, v.evidence, v.source,
           CASE WHEN v.days_ago IS NULL THEN NULL ELSE CURRENT_DATE - v.days_ago END,
           CASE WHEN v.days_ago IS NULL THEN NULL ELSE 'skills-board' END
    FROM (VALUES ('E1001', 'Java', 5, 'Oracle Certified Professional, Java SE 17', 'CERTIFICATION', 90),
                 ('E1001', 'Spring Boot', 5, 'Led the payments gateway rewrite on Spring Boot 3', 'PROJECT_DELIVERY', 120),
                 ('E1001', 'PostgreSQL', 4, 'Internal database assessment: 88 percent', 'ASSESSMENT', 200),
                 ('E1001', 'Kafka', 4, 'Built the settlement event pipeline', 'PROJECT_DELIVERY', 300),
                 ('E1001', 'Kubernetes', 2, 'Self-declared', 'SELF_DECLARED', NULL),
                 ('E1002', 'Java', 3, 'Code review history', 'MANAGER_REVIEW', 150),
                 ('E1002', 'Spring Boot', 3, 'Code review history', 'MANAGER_REVIEW', 150),
                 ('E1002', 'React', 4, 'Shipped the customer self-service portal', 'PROJECT_DELIVERY', 60),
                 ('E1002', 'TypeScript', 4, 'Frontend guild assessment', 'ASSESSMENT', 60),
                 ('E1002', 'Next.js', 3, 'Marketing site migration', 'PROJECT_DELIVERY', 200),
                 ('E1002', 'PostgreSQL', 3, 'Code review history', 'MANAGER_REVIEW', 150),
                 ('E1003', 'Java', 5, 'Principal reviewer for the Java guild', 'MANAGER_REVIEW', 30),
                 ('E1003', 'Spring Boot', 4, 'Spring Professional certification', 'CERTIFICATION', 30),
                 ('E1003', 'PostgreSQL', 3, 'Query tuning workshop', 'ASSESSMENT', 700),
                 ('E1003', 'Kafka', 3, 'Order events integration', 'PROJECT_DELIVERY', 60),
                 ('E1003', 'AWS', 3, 'AWS Cloud Practitioner', 'CERTIFICATION', 400),
                 ('E1004', 'React', 5, 'Design-system component library author', 'PROJECT_DELIVERY', 45),
                 ('E1004', 'TypeScript', 4, 'Frontend guild assessment', 'ASSESSMENT', 45),
                 ('E1004', 'Next.js', 4, 'Docs platform on Next.js', 'PROJECT_DELIVERY', 100),
                 ('E1004', 'UX Design', 2, 'Self-declared', 'SELF_DECLARED', NULL),
                 ('E1005', 'Spark', 4, 'Databricks Data Engineer Associate', 'CERTIFICATION', 120),
                 ('E1005', 'SQL', 5, 'Analytics SQL assessment', 'ASSESSMENT', 90),
                 ('E1005', 'Kafka', 3, 'Clickstream ingestion', 'PROJECT_DELIVERY', 200),
                 ('E1005', 'AWS', 3, 'Data platform on AWS', 'PROJECT_DELIVERY', 300),
                 ('E1005', 'Python', 3, 'Pipeline tooling', 'MANAGER_REVIEW', 300),
                 ('E1006', 'Machine Learning', 5, 'Fraud model owner, 3 production models', 'PROJECT_DELIVERY', 40),
                 ('E1006', 'Python', 5, 'Python guild assessment', 'ASSESSMENT', 40),
                 ('E1006', 'LLM Engineering', 4, 'Support copilot RAG system', 'PROJECT_DELIVERY', 80),
                 ('E1006', 'Spark', 3, 'Feature pipelines', 'PROJECT_DELIVERY', 250),
                 ('E1007', 'AWS', 4, 'AWS Solutions Architect Associate', 'CERTIFICATION', 100),
                 ('E1007', 'Terraform', 4, 'HashiCorp Terraform Associate', 'CERTIFICATION', 100),
                 ('E1007', 'Kubernetes', 4, 'CKA certification', 'CERTIFICATION', 160),
                 ('E1007', 'Docker', 4, 'Platform team review', 'MANAGER_REVIEW', 160),
                 ('E1008', 'AWS', 4, 'AWS SysOps Administrator', 'CERTIFICATION', 200),
                 ('E1008', 'Terraform', 3, 'Landing zone modules', 'PROJECT_DELIVERY', 200),
                 ('E1008', 'Kubernetes', 3, 'Cluster upgrades runbook', 'PROJECT_DELIVERY', 200),
                 ('E1008', 'Docker', 4, 'Platform team review', 'MANAGER_REVIEW', 200),
                 ('E1009', 'Test Automation', 5, 'Owns the regression framework', 'PROJECT_DELIVERY', 70),
                 ('E1009', 'Playwright', 4, 'Migrated E2E suite to Playwright', 'PROJECT_DELIVERY', 70),
                 ('E1009', 'Java', 3, 'Test tooling in Java', 'MANAGER_REVIEW', 300),
                 ('E1010', 'UX Design', 5, 'Design lead for the mobile app', 'PROJECT_DELIVERY', 50),
                 ('E1010', 'React', 2, 'Prototyping', 'SELF_DECLARED', NULL),
                 ('E1011', 'Business Analysis', 4, 'CBAP certification', 'CERTIFICATION', 220),
                 ('E1011', 'SQL', 3, 'Reporting requirements', 'MANAGER_REVIEW', 220),
                 ('E1011', 'Scrum', 3, 'Product owner backup', 'MANAGER_REVIEW', 220),
                 ('E1012', 'Scrum', 5, 'Professional Scrum Master II', 'CERTIFICATION', 120),
                 ('E1012', 'Business Analysis', 3, 'Discovery workshops', 'PROJECT_DELIVERY', 300),
                 ('E1013', 'Java', 4, 'Java guild assessment', 'ASSESSMENT', 200),
                 ('E1013', 'Spring Boot', 4, 'Order service owner', 'PROJECT_DELIVERY', 200),
                 ('E1013', 'PostgreSQL', 3, 'Code review history', 'MANAGER_REVIEW', 200),
                 ('E1014', 'SQL', 4, 'Analytics SQL assessment', 'ASSESSMENT', 100),
                 ('E1014', 'Python', 3, 'Notebook analyses', 'MANAGER_REVIEW', 100),
                 ('E1014', 'Spark', 2, 'Self-declared', 'SELF_DECLARED', NULL),
                 ('E1015', 'AWS', 5, 'AWS Solutions Architect Professional', 'CERTIFICATION', 60),
                 ('E1015', 'Terraform', 4, 'Terraform module maintainer', 'PROJECT_DELIVERY', 60),
                 ('E1015', 'Kubernetes', 5, 'CKA and CKS certifications', 'CERTIFICATION', 60),
                 ('E1015', 'Docker', 5, 'Base image program owner', 'PROJECT_DELIVERY', 60),
                 ('E1015', 'Go', 3, 'Internal operators in Go', 'PROJECT_DELIVERY', 200),
                 ('E1016', 'React', 5, 'Frontend lead, web platform', 'MANAGER_REVIEW', 30),
                 ('E1016', 'TypeScript', 5, 'TypeScript migration lead', 'PROJECT_DELIVERY', 30),
                 ('E1016', 'Next.js', 4, 'Storefront on Next.js', 'PROJECT_DELIVERY', 30),
                 ('E1017', 'Node.js', 4, 'Notifications service owner', 'PROJECT_DELIVERY', 90),
                 ('E1017', 'TypeScript', 3, 'Code review history', 'MANAGER_REVIEW', 90),
                 ('E1017', 'PostgreSQL', 3, 'Code review history', 'MANAGER_REVIEW', 90),
                 ('E1017', 'Rust', 2, 'Self-declared side projects', 'SELF_DECLARED', NULL),
                 ('E1017', 'Go', 2, 'Self-declared', 'SELF_DECLARED', NULL),
                 ('E1017', 'Docker', 3, 'Local tooling', 'MANAGER_REVIEW', 90),
                 ('E1018', 'Test Automation', 4, 'API test suite owner', 'PROJECT_DELIVERY', 120),
                 ('E1018', 'Playwright', 2, 'Self-declared', 'SELF_DECLARED', NULL),
                 ('E1019', 'Java', 4, 'Architecture review board', 'MANAGER_REVIEW', 150),
                 ('E1019', 'Spring Boot', 3, 'Architecture review board', 'MANAGER_REVIEW', 150),
                 ('E1019', 'AWS', 4, 'AWS Solutions Architect Associate', 'CERTIFICATION', 600),
                 ('E1019', 'Kubernetes', 3, 'Reference architecture', 'PROJECT_DELIVERY', 150),
                 ('E1020', 'Machine Learning', 4, 'Churn and propensity models', 'PROJECT_DELIVERY', 110),
                 ('E1020', 'Python', 5, 'Python guild assessment', 'ASSESSMENT', 110),
                 ('E1020', 'Spark', 3, 'Feature engineering at scale', 'PROJECT_DELIVERY', 110),
                 ('E1020', 'SQL', 4, 'Analytics SQL assessment', 'ASSESSMENT', 110),
                 ('E1021', 'AWS', 4, 'AWS Security Specialty', 'CERTIFICATION', 300),
                 ('E1021', 'Kubernetes', 3, 'Cluster hardening', 'PROJECT_DELIVERY', 300),
                 ('E1022', 'Java', 4, 'Java guild assessment', 'ASSESSMENT', 100),
                 ('E1022', 'Spring Boot', 4, 'ERP connector services', 'PROJECT_DELIVERY', 100),
                 ('E1022', 'PostgreSQL', 4, 'Database assessment', 'ASSESSMENT', 100),
                 ('E1022', 'Kafka', 2, 'Self-declared', 'SELF_DECLARED', NULL))
             AS v(code, skill, lvl, evidence, source, days_ago)
             JOIN employees e ON e.employee_code = v.code
             JOIN skills s ON s.name = v.skill;

    -- ------------------------------------------------------------ demands
    INSERT INTO project_demands (project_name, role, description, start_date, end_date, location, remote_allowed,
                                 allocation_percent, headcount, priority, status, created_by)
    VALUES ('Atlas Payments Platform', 'Senior Java Backend Engineer',
            'Build the card settlement and reconciliation services.', CURRENT_DATE + 7, CURRENT_DATE + 120,
            'Bengaluru', TRUE, 100, 2, 'CRITICAL', 'OPEN', 'manager'),
           ('Nimbus Customer Portal', 'Frontend Engineer',
            'Customer self-service portal on Next.js.', CURRENT_DATE + 14, CURRENT_DATE + 100,
            'Pune', TRUE, 100, 1, 'HIGH', 'OPEN', 'manager'),
           ('Orion Data Lakehouse', 'Data Engineer',
            'Streaming ingestion into the lakehouse.', CURRENT_DATE + 10, CURRENT_DATE + 150,
            'Pune', FALSE, 80, 1, 'HIGH', 'OPEN', 'manager'),
           ('Helios Cloud Migration', 'Cloud DevOps Engineer',
            'Migrate 40 services to the new AWS landing zone.', CURRENT_DATE + 3, CURRENT_DATE + 90,
            'Hyderabad', TRUE, 50, 2, 'MEDIUM', 'PARTIALLY_FILLED', 'manager'),
           ('Sentinel Fraud Detection', 'ML Engineer',
            'Real-time fraud scoring models.', CURRENT_DATE + 21, CURRENT_DATE + 180,
            'Bengaluru', TRUE, 100, 1, 'CRITICAL', 'OPEN', 'manager'),
           ('Quasar Mobile Banking', 'QA Automation Engineer',
            'End-to-end automation for the mobile banking release.', CURRENT_DATE + 5, CURRENT_DATE + 75,
            'Chennai', FALSE, 100, 1, 'MEDIUM', 'OPEN', 'manager'),
           ('Vega Embedded Telemetry', 'Rust Systems Engineer',
            'Low-latency telemetry agent for edge devices.', CURRENT_DATE + 30, CURRENT_DATE + 200,
            'Bengaluru', FALSE, 100, 1, 'HIGH', 'OPEN', 'manager'),
           ('Aurora Analytics Revamp', 'Business Analyst',
            'Requirements for the analytics revamp.', CURRENT_DATE + 2, CURRENT_DATE + 60,
            'Chennai', TRUE, 50, 1, 'LOW', 'OPEN', 'manager'),
           ('Lumen Design System', 'Product Designer',
            'Unified design system across web and mobile.', CURRENT_DATE + 14, CURRENT_DATE + 90,
            'Bengaluru', TRUE, 60, 1, 'MEDIUM', 'OPEN', 'manager'),
           ('Titan ERP Integration', 'Integration Engineer',
            'ERP connectors for finance and procurement.', CURRENT_DATE - 30, CURRENT_DATE + 60,
            'Bengaluru', TRUE, 100, 1, 'HIGH', 'FILLED', 'manager'),
           ('Phoenix Core Banking', 'Java Engineer',
            'Core banking maintenance stream.', CURRENT_DATE - 60, CURRENT_DATE + 20,
            'Bengaluru', TRUE, 50, 1, 'MEDIUM', 'FILLED', 'manager');

    INSERT INTO demand_skills (demand_id, skill_id, minimum_proficiency, mandatory)
    SELECT d.id, s.id, v.min_level, v.mandatory
    FROM (VALUES ('Atlas Payments Platform', 'Java', 4, TRUE),
                 ('Atlas Payments Platform', 'Spring Boot', 4, TRUE),
                 ('Atlas Payments Platform', 'PostgreSQL', 3, TRUE),
                 ('Atlas Payments Platform', 'Kafka', 3, FALSE),
                 ('Atlas Payments Platform', 'Kubernetes', 2, FALSE),
                 ('Nimbus Customer Portal', 'React', 4, TRUE),
                 ('Nimbus Customer Portal', 'TypeScript', 4, TRUE),
                 ('Nimbus Customer Portal', 'Next.js', 3, FALSE),
                 ('Nimbus Customer Portal', 'UX Design', 2, FALSE),
                 ('Orion Data Lakehouse', 'Spark', 3, TRUE),
                 ('Orion Data Lakehouse', 'SQL', 4, TRUE),
                 ('Orion Data Lakehouse', 'Kafka', 3, TRUE),
                 ('Orion Data Lakehouse', 'AWS', 3, FALSE),
                 ('Helios Cloud Migration', 'AWS', 4, TRUE),
                 ('Helios Cloud Migration', 'Terraform', 3, TRUE),
                 ('Helios Cloud Migration', 'Kubernetes', 3, TRUE),
                 ('Helios Cloud Migration', 'Docker', 3, FALSE),
                 ('Sentinel Fraud Detection', 'Machine Learning', 4, TRUE),
                 ('Sentinel Fraud Detection', 'Python', 4, TRUE),
                 ('Sentinel Fraud Detection', 'LLM Engineering', 3, FALSE),
                 ('Sentinel Fraud Detection', 'Spark', 3, FALSE),
                 ('Quasar Mobile Banking', 'Test Automation', 4, TRUE),
                 ('Quasar Mobile Banking', 'Playwright', 3, TRUE),
                 ('Quasar Mobile Banking', 'Java', 3, FALSE),
                 ('Vega Embedded Telemetry', 'Rust', 4, TRUE),
                 ('Vega Embedded Telemetry', 'Go', 3, FALSE),
                 ('Aurora Analytics Revamp', 'Business Analysis', 3, TRUE),
                 ('Aurora Analytics Revamp', 'SQL', 2, FALSE),
                 ('Aurora Analytics Revamp', 'Scrum', 2, FALSE),
                 ('Lumen Design System', 'UX Design', 4, TRUE),
                 ('Lumen Design System', 'React', 2, FALSE),
                 ('Titan ERP Integration', 'Java', 4, TRUE),
                 ('Titan ERP Integration', 'Spring Boot', 3, TRUE),
                 ('Phoenix Core Banking', 'Java', 3, TRUE))
             AS v(project, skill, min_level, mandatory)
             JOIN project_demands d ON d.project_name = v.project
             JOIN skills s ON s.name = v.skill;

    -- ------------------------------------------------------------ allocations (with history)
    INSERT INTO allocations (employee_id, demand_id, start_date, end_date, allocation_percent, status,
                             requested_by, requested_at, request_note, decided_by, decided_at, decision_note,
                             cancelled_by, cancelled_at, cancellation_reason)
    SELECT e.id, d.id, d.start_date, d.end_date, v.pct, v.status,
           'manager', now() - (v.requested_days_ago || ' days')::interval, v.request_note,
           v.decided_by, CASE WHEN v.decided_by IS NULL THEN NULL ELSE now() - ((v.requested_days_ago - 1) || ' days')::interval END,
           v.decision_note,
           CASE WHEN v.status = 'CANCELLED' THEN 'manager' END,
           CASE WHEN v.status = 'CANCELLED' THEN now() - INTERVAL '2 days' END,
           v.cancellation_reason
    FROM (VALUES ('E1022', 'Titan ERP Integration', 100, 'APPROVED', 35, 'Best Java fit available', 'approver', 'Approved for full period', NULL),
                 ('E1001', 'Phoenix Core Banking', 50, 'APPROVED', 65, 'Part-time maintenance', 'approver', 'Approved', NULL),
                 ('E1008', 'Helios Cloud Migration', 50, 'APPROVED', 6, 'Landing zone experience', 'approver', 'Approved', NULL),
                 ('E1007', 'Helios Cloud Migration', 50, 'PENDING_APPROVAL', 2, 'Second engineer for migration waves', NULL, NULL, NULL),
                 ('E1016', 'Nimbus Customer Portal', 100, 'PENDING_APPROVAL', 1, 'Frontend lead requested by client', NULL, NULL, NULL),
                 ('E1005', 'Orion Data Lakehouse', 80, 'CANCELLED', 10, 'Initial staffing', 'approver', 'Approved', 'Client moved the start date; re-plan staffing'),
                 ('E1018', 'Quasar Mobile Banking', 100, 'REJECTED', 4, 'Available immediately', 'approver', 'Playwright level below the requirement', NULL))
             AS v(code, project, pct, status, requested_days_ago, request_note, decided_by, decision_note, cancellation_reason)
             JOIN employees e ON e.employee_code = v.code
             JOIN project_demands d ON d.project_name = v.project;

    INSERT INTO allocation_events (allocation_id, event_type, from_status, to_status, actor, note, occurred_at)
    SELECT a.id, 'REQUESTED', NULL, 'PENDING_APPROVAL', a.requested_by, a.request_note, a.requested_at
    FROM allocations a;

    INSERT INTO allocation_events (allocation_id, event_type, from_status, to_status, actor, note, occurred_at)
    SELECT a.id,
           CASE WHEN a.status = 'REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END,
           'PENDING_APPROVAL',
           CASE WHEN a.status = 'REJECTED' THEN 'REJECTED' ELSE 'APPROVED' END,
           a.decided_by, a.decision_note, a.decided_at
    FROM allocations a
    WHERE a.decided_by IS NOT NULL;

    INSERT INTO allocation_events (allocation_id, event_type, from_status, to_status, actor, note, occurred_at)
    SELECT a.id, 'CANCELLED', 'APPROVED', 'CANCELLED', a.cancelled_by, a.cancellation_reason, a.cancelled_at
    FROM allocations a
    WHERE a.status = 'CANCELLED';

    -- ------------------------------------------------------------ learning catalogue (no entries for Go on purpose)
    INSERT INTO learning_resources (skill_id, title, provider, format, target_level, duration_hours, url)
    SELECT s.id, v.title, v.provider, v.format, v.lvl, v.hours, v.url
    FROM (VALUES ('Java', 'Modern Java: Records, Streams and Virtual Threads', 'Internal Academy', 'COURSE', 3, 16, NULL),
                 ('Java', 'Java SE 17 Developer Certification Prep', 'Certification Partner', 'CERTIFICATION', 4, 40, NULL),
                 ('Spring Boot', 'Spring Boot in Production', 'Internal Academy', 'COURSE', 3, 20, NULL),
                 ('Spring Boot', 'Spring Professional Certification Prep', 'Certification Partner', 'CERTIFICATION', 4, 32, NULL),
                 ('Spring Boot', 'Architecture Mentoring: Service Design', 'Engineering Guild', 'MENTORING', 5, 24, NULL),
                 ('PostgreSQL', 'PostgreSQL Indexing and Query Tuning', 'Internal Academy', 'LAB', 3, 12, NULL),
                 ('PostgreSQL', 'PostgreSQL Internals Deep Dive', 'Data Guild', 'COURSE', 4, 18, NULL),
                 ('Kafka', 'Event Streaming Fundamentals', 'Internal Academy', 'COURSE', 2, 10, NULL),
                 ('Kafka', 'Kafka Developer Certification Prep', 'Certification Partner', 'CERTIFICATION', 3, 30, NULL),
                 ('Kafka', 'Exactly-once Pipelines Lab', 'Data Guild', 'LAB', 4, 14, NULL),
                 ('Kubernetes', 'Kubernetes for Developers', 'Internal Academy', 'COURSE', 2, 12, NULL),
                 ('Kubernetes', 'Certified Kubernetes Administrator Prep', 'Certification Partner', 'CERTIFICATION', 3, 40, NULL),
                 ('Kubernetes', 'Production Cluster Operations Shadowing', 'Platform Team', 'PROJECT', 4, 60, NULL),
                 ('React', 'React Patterns and Performance', 'Internal Academy', 'COURSE', 3, 16, NULL),
                 ('React', 'Design-System Contribution Sprint', 'Web Platform', 'PROJECT', 4, 40, NULL),
                 ('TypeScript', 'TypeScript for Large Codebases', 'Internal Academy', 'COURSE', 3, 14, NULL),
                 ('TypeScript', 'Advanced Types Workshop', 'Web Platform', 'LAB', 4, 8, NULL),
                 ('Next.js', 'Next.js App Router Essentials', 'Internal Academy', 'COURSE', 3, 10, NULL),
                 ('Spark', 'Spark Fundamentals', 'Internal Academy', 'COURSE', 2, 12, NULL),
                 ('Spark', 'Databricks Data Engineer Associate Prep', 'Certification Partner', 'CERTIFICATION', 3, 30, NULL),
                 ('Spark', 'Streaming at Scale Lab', 'Data Guild', 'LAB', 4, 16, NULL),
                 ('SQL', 'Analytical SQL Mastery', 'Internal Academy', 'COURSE', 4, 12, NULL),
                 ('AWS', 'AWS Solutions Architect Associate Prep', 'Certification Partner', 'CERTIFICATION', 4, 40, NULL),
                 ('AWS', 'Well-Architected Reviews Shadowing', 'Cloud Platform', 'MENTORING', 5, 20, NULL),
                 ('Terraform', 'Terraform Associate Prep', 'Certification Partner', 'CERTIFICATION', 3, 20, NULL),
                 ('Terraform', 'Module Design Lab', 'Cloud Platform', 'LAB', 4, 10, NULL),
                 ('Docker', 'Container Fundamentals', 'Internal Academy', 'COURSE', 3, 8, NULL),
                 ('Machine Learning', 'Applied ML Engineering', 'Internal Academy', 'COURSE', 3, 30, NULL),
                 ('Machine Learning', 'Model Monitoring in Production', 'Data Guild', 'PROJECT', 4, 40, NULL),
                 ('Python', 'Python for Engineers', 'Internal Academy', 'COURSE', 3, 16, NULL),
                 ('Python', 'Python Performance and Packaging', 'Data Guild', 'LAB', 4, 10, NULL),
                 ('LLM Engineering', 'Building Guarded LLM Agents', 'AI Guild', 'COURSE', 3, 16, NULL),
                 ('Test Automation', 'Test Strategy and Automation Architecture', 'Quality Guild', 'COURSE', 4, 20, NULL),
                 ('Playwright', 'Playwright End-to-End Testing', 'Quality Guild', 'COURSE', 3, 12, NULL),
                 ('Playwright', 'Visual and API Testing with Playwright', 'Quality Guild', 'LAB', 4, 8, NULL),
                 ('Rust', 'Rust Fundamentals', 'Internal Academy', 'COURSE', 2, 20, NULL),
                 ('Rust', 'Ownership, Async and Unsafe Rust', 'Systems Guild', 'COURSE', 3, 24, NULL),
                 ('Rust', 'Embedded Rust Project Rotation', 'Systems Guild', 'PROJECT', 4, 80, NULL),
                 ('UX Design', 'Interaction Design Foundations', 'Design Guild', 'COURSE', 3, 20, NULL),
                 ('UX Design', 'Design Critique Mentoring', 'Design Guild', 'MENTORING', 4, 16, NULL),
                 ('Business Analysis', 'Requirements Engineering', 'Delivery Guild', 'COURSE', 3, 16, NULL),
                 ('Scrum', 'Professional Scrum Master Prep', 'Certification Partner', 'CERTIFICATION', 3, 16, NULL))
             AS v(skill, title, provider, format, lvl, hours, url)
             JOIN skills s ON s.name = v.skill;

    -- ------------------------------------------------------------ training recommendations
    INSERT INTO training_recommendations (employee_id, skill_id, demand_id, learning_resource_id, current_level,
                                          target_level, reason, source, status, created_by)
    SELECT e.id, s.id, d.id, lr.id, v.current_level, v.target_level, v.reason, v.source, v.status, 'manager'
    FROM (VALUES ('E1017', 'Rust', 'Vega Embedded Telemetry', 'Ownership, Async and Unsafe Rust', 2, 4,
                  'Mandatory gap for Vega Embedded Telemetry: requires level 4, stored level 2', 'AI_AGENT', 'RECOMMENDED'),
                 ('E1014', 'Spark', 'Orion Data Lakehouse', 'Databricks Data Engineer Associate Prep', 2, 3,
                  'Mandatory gap for Orion Data Lakehouse: requires level 3, stored level 2', 'MANUAL', 'IN_PROGRESS'))
             AS v(code, skill, project, resource, current_level, target_level, reason, source, status)
             JOIN employees e ON e.employee_code = v.code
             JOIN skills s ON s.name = v.skill
             JOIN project_demands d ON d.project_name = v.project
             JOIN learning_resources lr ON lr.title = v.resource;
END
$$;
