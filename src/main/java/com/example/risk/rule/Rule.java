package com.example.risk.rule;

import com.example.risk.model.Features;
import com.example.risk.model.RuleHit;
import com.example.risk.model.TransactionEvent;

import java.util.Optional;

/** A stateless, side-effect-free rule. Implementations must be thread-safe (they are shared). */
public interface Rule {
    String id();

    Optional<RuleHit> evaluate(TransactionEvent event, Features features);
}
