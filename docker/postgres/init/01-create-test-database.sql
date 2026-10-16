-- Runs once, when the PostgreSQL volume is first created.
-- A separate database for the integration tests (TEST_DB_URL), so tests never touch demo data.
CREATE DATABASE talent_management_test;
