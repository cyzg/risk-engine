package com.example.risk.rule;

import com.example.risk.model.Decision;
import com.example.risk.model.Features;
import com.example.risk.model.RuleHit;
import com.example.risk.model.TransactionEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates events against the current {@link RuleSet}.
 *
 * <p>Hot reload without locks: the rule set is an immutable snapshot held in a
 * {@code volatile} field. A writer publishes a new snapshot with a single reference
 * assignment (the volatile write happens-before every subsequent volatile read), and each
 * decision reads the reference once, so it always sees one consistent version of all rules.
 */
@Component
public class RuleEngine {

    public record Evaluation(Decision decision, List<RuleHit> hits, long ruleVersion) {
    }

    private volatile RuleSet current;

    public RuleEngine(RuleConfig initial) {
        this.current = RuleFactory.build(initial, 1);
    }

    public Evaluation decide(TransactionEvent event, Features features) {
        RuleSet snapshot = current; // single volatile read
        Decision result = Decision.PASS;
        List<RuleHit> hits = new ArrayList<>();
        for (Rule rule : snapshot.rules()) {
            var hit = rule.evaluate(event, features);
            if (hit.isPresent()) {
                hits.add(hit.get());
                result = Decision.max(result, hit.get().decision());
            }
        }
        return new Evaluation(result, hits, snapshot.version());
    }

    /** Replace the rule set; the version is bumped monotonically. Writers are serialized. */
    public synchronized long reload(RuleConfig config) {
        long next = current.version() + 1;
        current = RuleFactory.build(config, next);
        return next;
    }

    public long version() {
        return current.version();
    }
}
