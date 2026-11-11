package com.example.risk.rule;

import java.util.List;

/** Immutable snapshot of the active rules. Swapped atomically by {@link RuleEngine}. */
public record RuleSet(long version, int rejectScore, List<Rule> rules) {
    public RuleSet {
        rules = List.copyOf(rules);
    }
}
