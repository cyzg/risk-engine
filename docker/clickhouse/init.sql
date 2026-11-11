-- Analytical store: append-only decision log for rule-effect analysis.
CREATE DATABASE IF NOT EXISTS risk;

CREATE TABLE IF NOT EXISTS risk.decision_log
(
    event_id     String,
    user_id      String,
    decision     LowCardinality(String),
    shadow       UInt8,
    rule_version UInt64,
    hit_rules    Array(String),
    latency_ms   UInt32,
    decided_at   DateTime64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(decided_at)
ORDER BY (decided_at, user_id);
