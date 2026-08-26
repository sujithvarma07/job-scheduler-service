ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS depends_on_job_id UUID;

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_depends_on_job_id
        FOREIGN KEY (depends_on_job_id) REFERENCES jobs (id);

CREATE INDEX IF NOT EXISTS idx_jobs_depends_on_job_id
    ON jobs (depends_on_job_id);
