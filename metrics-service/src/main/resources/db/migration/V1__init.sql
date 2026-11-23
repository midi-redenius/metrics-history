CREATE TABLE metric (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(128)    NOT NULL,
    unit        VARCHAR(32),
    host        VARCHAR(128)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (name, host)
);

CREATE INDEX idx_metric_host ON metric (host);

-- The primary key (metric_id, ts) also serves history range scans; no separate ts index.
CREATE TABLE metric_point (
    metric_id   BIGINT           NOT NULL REFERENCES metric(id) ON DELETE CASCADE,
    ts          TIMESTAMPTZ      NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (metric_id, ts)
);
