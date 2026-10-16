-- Agent runs are inserted before the agent starts; RUNNING distinguishes in-flight runs from failures.
ALTER TABLE ai_agent_runs DROP CONSTRAINT ck_ai_runs_status;
ALTER TABLE ai_agent_runs
    ADD CONSTRAINT ck_ai_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'COMPLETED_WITH_FALLBACK', 'FAILED'));
