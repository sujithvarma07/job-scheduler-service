CREATE TABLE IF NOT EXISTS job_audit_log (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    job_id          UUID            NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    message         TEXT,
    timestamp       TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_job_audit_log PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_job_audit_log_job_id
    ON job_audit_log (job_id, timestamp ASC);
