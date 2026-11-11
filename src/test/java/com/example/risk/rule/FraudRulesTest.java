package com.example.risk.rule;

import com.example.risk.model.Decision;
import com.example.risk.model.Features;
import com.example.risk.model.RuleHit;
import com.example.risk.model.TransactionEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudRulesTest {

    private static final RuleConfig CONFIG = new RuleConfig(
            new BigDecimal("5000"), new BigDecimal("20000"), 10L, 3L, Set.of(),
            1L, 5L, new BigDecimal("2000"), 80);

    private final RuleEngine engine = new RuleEngine(CONFIG);

    private static TransactionEvent event(String amount) {
        return new TransactionEvent("e1", "u1", "d1", new BigDecimal(amount), "CNY", "1.1.1.1", "CN", Instant.now());
    }

    private static Features features(long countries5m, long ips1h, boolean newDevice) {
        return new Features(1, 1, 1, countries5m, ips1h, newDevice);
    }

    private static boolean fired(RuleEngine.Evaluation eval, String ruleId) {
        return eval.hits().stream().map(RuleHit::ruleId).anyMatch(ruleId::equals);
    }

    @Test
    void singleCountryDoesNotTriggerGeoHopping() {
        var eval = engine.decide(event("100"), features(1, 1, false));
        assertEquals(Decision.PASS, eval.decision());
        assertEquals(0, eval.riskScore());
    }

    @Test
    void twoCountriesWithinFiveMinutesTriggersReview() {
        var eval = engine.decide(event("100"), features(2, 1, false));
        assertEquals(Decision.REVIEW, eval.decision());
        assertTrue(fired(eval, "GEO_HOPPING"));
        assertEquals(40, eval.riskScore());
    }

    @Test
    void manyIpsTriggersReview() {
        assertEquals(Decision.PASS, engine.decide(event("100"), features(1, 5, false)).decision()); // at the limit
        var eval = engine.decide(event("100"), features(1, 6, false));
        assertEquals(Decision.REVIEW, eval.decision());
        assertTrue(fired(eval, "IP_HOPPING"));
    }

    @Test
    void newDeviceOnlyMattersForLargeAmounts() {
        assertEquals(Decision.PASS, engine.decide(event("500"), features(1, 1, true)).decision());
        var eval = engine.decide(event("3000"), features(1, 1, true));
        assertEquals(Decision.REVIEW, eval.decision());
        assertTrue(fired(eval, "NEW_DEVICE_LARGE_AMOUNT"));
    }

    @Test
    void knownDeviceWithLargeAmountIsNotFlaggedByDeviceRule() {
        var eval = engine.decide(event("3000"), features(1, 1, false));
        assertEquals(Decision.PASS, eval.decision());
    }

    @Test
    void combinedWeakSignalsEscalateToReject() {
        // geo (40) + new device & large amount (40) = 80 >= rejectScore: neither alone would reject
        var eval = engine.decide(event("3000"), features(2, 1, true));
        assertEquals(2, eval.hits().size());
        assertEquals(80, eval.riskScore());
        assertEquals(Decision.REJECT, eval.decision());
    }

    @Test
    void scoreIsCappedAtHundred() {
        // amount>=20000 (80) + geo (40) + ips (30) + new device (40) would be 190
        var eval = engine.decide(event("30000"), features(3, 9, true));
        assertEquals(100, eval.riskScore());
        assertEquals(Decision.REJECT, eval.decision());
    }

    @Test
    void rejectScoreIsHotReloadable() {
        var strict = new RuleConfig(new BigDecimal("5000"), new BigDecimal("20000"), 10L, 3L, Set.of(),
                1L, 5L, new BigDecimal("2000"), 40);
        engine.reload(strict);
        // a single geo hit scores 40 -> now escalates to REJECT
        assertEquals(Decision.REJECT, engine.decide(event("100"), features(2, 1, false)).decision());
    }
}
