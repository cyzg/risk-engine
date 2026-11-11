-- Transactional data: rules, lists, review cases.
CREATE TABLE IF NOT EXISTS rule_config (
    id          BIGSERIAL PRIMARY KEY,
    version     BIGINT      NOT NULL UNIQUE,
    config      JSONB       NOT NULL,
    created_by  TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS blacklist (
    id          BIGSERIAL PRIMARY KEY,
    kind        TEXT        NOT NULL,           -- USER / DEVICE / IP
    value       TEXT        NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (kind, value)
);

CREATE TABLE IF NOT EXISTS review_case (
    id          BIGSERIAL PRIMARY KEY,
    event_id    TEXT        NOT NULL UNIQUE,
    user_id     TEXT        NOT NULL,
    decision    TEXT        NOT NULL,
    hits        JSONB       NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'OPEN',  -- OPEN / APPROVED / REJECTED
    assignee    TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_review_case_status ON review_case (status, created_at);
