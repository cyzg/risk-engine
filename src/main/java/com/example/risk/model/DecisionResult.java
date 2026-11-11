package com.example.risk.model;

import java.time.Instant;
import java.util.List;

/** Outbound decision (Kafka decision topic; later sunk to ClickHouse for analysis). */
public record DecisionResult(
        String eventId,
        String userId,
        Decision decision,
        boolean shadow,
        long ruleVersion,
        List<RuleHit> hits,
        long latencyMs,
        Instant decidedAt) {
}
