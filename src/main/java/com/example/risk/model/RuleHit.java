package com.example.risk.model;

/** One rule that fired. {@code score} is its contribution (0-100) to the overall risk score. */
public record RuleHit(String ruleId, Decision decision, String reason, int score) {
}
