package com.example.risk.feature;

import com.example.risk.model.Features;
import com.example.risk.model.TransactionEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Computes features in parallel on a dedicated bounded pool. Each lookup has its own timeout;
 * on timeout/failure it degrades to 0 instead of failing the whole decision.
 */
@Service
public class RedisFeatureService implements FeatureService {
    private static final Logger log = LoggerFactory.getLogger(RedisFeatureService.class);

    private static final long ONE_MINUTE_MS = 60_000;
    private static final long ONE_HOUR_MS = 3_600_000;

    private final StringRedisTemplate redis;
    private final Executor executor;
    private final long timeoutMs;
    private final DefaultRedisScript<Long> windowScript;
    private final Counter degraded;

    public RedisFeatureService(StringRedisTemplate redis,
                               @Qualifier("featureExecutor") Executor executor,
                               @Value("${risk.feature.timeout-ms:50}") long timeoutMs,
                               MeterRegistry registry) {
        this.redis = redis;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.windowScript = new DefaultRedisScript<>();
        this.windowScript.setLocation(new ClassPathResource("lua/sliding_window.lua"));
        this.windowScript.setResultType(Long.class);
        this.degraded = Counter.builder("risk.feature.degraded").register(registry);
    }

    @Override
    public CompletableFuture<Features> fetch(TransactionEvent e) {
        long now = e.occurredAt() != null ? e.occurredAt().toEpochMilli() : System.currentTimeMillis();

        CompletableFuture<Long> c1m = async(() -> window("risk:txn:1m:" + e.userId(), now, ONE_MINUTE_MS, e.eventId()));
        CompletableFuture<Long> c1h = async(() -> window("risk:txn:1h:" + e.userId(), now, ONE_HOUR_MS, e.eventId()));
        CompletableFuture<Long> dev = async(() -> deviceAccounts(e));

        return c1m.thenCombine(c1h, (a, b) -> new long[]{a, b})
                .thenCombine(dev, (ab, d) -> new Features(ab[0], ab[1], d));
    }

    private CompletableFuture<Long> async(java.util.function.Supplier<Long> task) {
        return CompletableFuture.supplyAsync(task, executor)
                .completeOnTimeout(null, timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(t -> null)
                .thenApply(v -> {
                    if (v == null) {
                        degraded.increment(); // timeout or error -> degrade to 0
                        return 0L;
                    }
                    return v;
                });
    }

    private long window(String key, long now, long windowMs, String member) {
        Long n = redis.execute(windowScript, List.of(key),
                String.valueOf(now), String.valueOf(windowMs), member);
        return n == null ? 0 : n;
    }

    private long deviceAccounts(TransactionEvent e) {
        if (e.deviceId() == null) {
            return 0;
        }
        String key = "risk:device:" + e.deviceId();
        redis.opsForSet().add(key, e.userId());
        redis.expire(key, Duration.ofDays(1));
        Long n = redis.opsForSet().size(key);
        return n == null ? 0 : n;
    }
}
