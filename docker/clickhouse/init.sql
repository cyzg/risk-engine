-- Analytical store: append-only decision log for rule-effect analysis.
CREATE DATABASE IF NOT EXISTS risk;

-- ReplacingMergeTree: Kafka delivery is at-least-once, so a redelivered batch can insert the
-- same event twice. Rows with the same sorting key (user_id, event_id) are collapsed during
-- background merges; use `FINAL` (or argMax) for exact results before a merge has happened.
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
ENGINE = ReplacingMergeTree(decided_at)
PARTITION BY toYYYYMM(decided_at)
ORDER BY (user_id, event_id);
