package com.example.risk.model;

public record RuleHit(String ruleId, Decision decision, String reason) {
}
