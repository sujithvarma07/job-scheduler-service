CREATE TABLE IF NOT EXISTS jobs (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL,
    payload         TEXT,
    status          VARCHAR(50)     NOT NULL,
    priority        INT             NOT NULL DEFAULT 5,
    max_retries     INT             NOT NULL DEFAULT 3,
    retry_count     INT             NOT NULL DEFAULT 0,
    scheduled_at    TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL,
    error_message   TEXT,

    CONSTRAINT pk_jobs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_jobs_status_priority
    ON jobs (status, priority DESC, created_at ASC);
