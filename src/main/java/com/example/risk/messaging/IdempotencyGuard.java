package com.example.risk.messaging;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Kafka is at-least-once; this makes re-delivered events a no-op. */
@Component
public class IdempotencyGuard {
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;

    public IdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return true if this is the first time we see the event. */
    public boolean firstTime(String eventId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("risk:seen:" + eventId, "1", TTL));
    }

    /** Release the mark so a retry can reprocess after a failure. */
    public void release(String eventId) {
        redis.delete("risk:seen:" + eventId);
    }
}
