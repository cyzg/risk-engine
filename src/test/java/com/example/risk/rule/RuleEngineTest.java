package com.example.risk.rule;

import com.example.risk.model.Decision;
import com.example.risk.model.Features;
import com.example.risk.model.TransactionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    private static final RuleConfig CONFIG = new RuleConfig(
            new BigDecimal("5000"), new BigDecimal("20000"), 10L, 3L, Set.of("bad_user"));

    private static TransactionEvent event(String user, String amount) {
        return new TransactionEvent("e1", user, "d1", new BigDecimal(amount), "CNY", "1.1.1.1", "CN", Instant.now());
    }

    @Test
    void normalTransactionPasses() {
        var engine = new RuleEngine(CONFIG);
        assertEquals(Decision.PASS, engine.decide(event("u1", "100"), Features.EMPTY).decision());
    }

    @Test
    void largeAmountIsRejected() {
        var engine = new RuleEngine(CONFIG);
        assertEquals(Decision.REJECT, engine.decide(event("u1", "30000"), Features.EMPTY).decision());
    }

    @Test
    void mostSevereHitWins() {
        var engine = new RuleEngine(CONFIG);
        // amount -> REVIEW, blacklist -> REJECT
        var eval = engine.decide(event("bad_user", "6000"), Features.EMPTY);
        assertEquals(Decision.REJECT, eval.decision());
        assertEquals(2, eval.hits().size());
    }

    @Test
    void highVelocityTriggersReview() {
        var engine = new RuleEngine(CONFIG);
        var eval = engine.decide(event("u1", "10"), new Features(11, 11, 1));
        assertEquals(Decision.REVIEW, eval.decision());
    }

    @Test
    void hotReloadChangesBehaviourAndBumpsVersion() {
        var engine = new RuleEngine(CONFIG);
        assertEquals(Decision.PASS, engine.decide(event("u1", "30"), Features.EMPTY).decision());

        long v = engine.reload(new RuleConfig(new BigDecimal("10"), new BigDecimal("20"), 10L, 3L, Set.of()));
        assertEquals(2, v);
        var eval = engine.decide(event("u1", "30"), Features.EMPTY);
        assertEquals(Decision.REJECT, eval.decision());
        assertEquals(2, eval.ruleVersion());
    }

    @Test
    void concurrentReloadAndDecideNeverSeesTornState() throws Exception {
        var engine = new RuleEngine(CONFIG);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) {
            final boolean writer = i == 0;
            pool.submit(() -> {
                for (int n = 0; n < 5_000; n++) {
                    if (writer) {
                        engine.reload(CONFIG);
                    } else {
                        var eval = engine.decide(event("u1", "30000"), Features.EMPTY);
                        // every snapshot contains the amount rule, so this must always hold
                        assertEquals(Decision.REJECT, eval.decision());
                    }
                }
                done.countDown();
            });
        }
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdownNow();
    }
}
