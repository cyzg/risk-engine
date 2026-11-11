package com.example.risk.rule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class RuleFactory {
    private RuleFactory() {
    }

    public static RuleSet build(RuleConfig c, long version) {
        BigDecimal review = c.reviewAmount() != null ? c.reviewAmount() : new BigDecimal("5000");
        BigDecimal reject = c.rejectAmount() != null ? c.rejectAmount() : new BigDecimal("20000");
        long velocity = c.velocityPerMinute() != null ? c.velocityPerMinute() : 10;
        long device = c.deviceAccountLimit() != null ? c.deviceAccountLimit() : 3;
        Set<String> black = c.blacklistedUsers() != null ? c.blacklistedUsers() : Set.of();

        return new RuleSet(version, List.of(
                new Rules.Blacklist(black),
                new Rules.Amount(review, reject),
                new Rules.Velocity(velocity),
                new Rules.DeviceSharing(device)));
    }
}
