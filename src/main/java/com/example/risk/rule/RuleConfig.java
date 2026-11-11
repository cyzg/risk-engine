package com.example.risk.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Rule parameters. Bound from {@code risk.rules.*} at startup, and also used as the
 * request body of {@code PUT /admin/rules} for hot updates.
 */
@ConfigurationProperties(prefix = "risk.rules")
public record RuleConfig(
        BigDecimal reviewAmount,
        BigDecimal rejectAmount,
        Long velocityPerMinute,
        Long deviceAccountLimit,
        Set<String> blacklistedUsers) {
}
