package com.example.risk.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Inbound transaction event (JSON on the Kafka input topic, keyed by userId). */
public record TransactionEvent(
        String eventId,
        String userId,
        String deviceId,
        BigDecimal amount,
        String currency,
        String ip,
        String country,
        Instant occurredAt) {
}
