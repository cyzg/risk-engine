package com.example.risk.service;

import com.example.risk.feature.FeatureService;
import com.example.risk.model.Decision;
import com.example.risk.model.DecisionResult;
import com.example.risk.model.Features;
import com.example.risk.model.TransactionEvent;
import com.example.risk.rule.RuleEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DecisionService {
    private final FeatureService features;
    private final RuleEngine engine;
    private final MeterRegistry registry;
    private final Timer latency;
    private final boolean shadowMode;

    public DecisionService(FeatureService features, RuleEngine engine, MeterRegistry registry,
                           @Value("${risk.shadow-mode:false}") boolean shadowMode) {
        this.features = features;
        this.engine = engine;
        this.registry = registry;
        this.shadowMode = shadowMode;
        this.latency = Timer.builder("risk.decision.latency")
                .publishPercentileHistogram().register(registry);
    }

    public DecisionResult decide(TransactionEvent event) {
        long start = System.nanoTime();
        Features f = features.fetch(event).join();
        RuleEngine.Evaluation eval = engine.decide(event, f);

        // Shadow mode: record what the rules would do, but never block traffic.
        Decision effective = shadowMode ? Decision.PASS : eval.decision();
        long elapsed = System.nanoTime() - start;
        latency.record(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS);
        registry.counter("risk.decision", "result", eval.decision().name(),
                "shadow", String.valueOf(shadowMode)).increment();

        return new DecisionResult(event.eventId(), event.userId(), effective, shadowMode,
                eval.ruleVersion(), eval.hits(), elapsed / 1_000_000, Instant.now());
    }
}
