package com.example.risk.rule;

import com.example.risk.model.Decision;
import com.example.risk.model.Features;
import com.example.risk.model.RuleHit;
import com.example.risk.model.TransactionEvent;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** Built-in rule implementations. */
public final class Rules {
    private Rules() {
    }

    public record Blacklist(Set<String> users) implements Rule {
        public Blacklist {
            users = Set.copyOf(users);
        }

        @Override
        public String id() {
            return "BLACKLIST_USER";
        }

        @Override
        public Optional<RuleHit> evaluate(TransactionEvent e, Features f) {
            return users.contains(e.userId())
                    ? Optional.of(new RuleHit(id(), Decision.REJECT, "user in blacklist"))
                    : Optional.empty();
        }
    }

    public record Amount(BigDecimal reviewAt, BigDecimal rejectAt) implements Rule {
        @Override
        public String id() {
            return "AMOUNT_THRESHOLD";
        }

        @Override
        public Optional<RuleHit> evaluate(TransactionEvent e, Features f) {
            if (e.amount() == null) {
                return Optional.empty();
            }
            if (e.amount().compareTo(rejectAt) >= 0) {
                return Optional.of(new RuleHit(id(), Decision.REJECT, "amount >= " + rejectAt));
            }
            if (e.amount().compareTo(reviewAt) >= 0) {
                return Optional.of(new RuleHit(id(), Decision.REVIEW, "amount >= " + reviewAt));
            }
            return Optional.empty();
        }
    }

    /** Too many transactions by one user in the last minute. */
    public record Velocity(long maxPerMinute) implements Rule {
        @Override
        public String id() {
            return "VELOCITY_1M";
        }

        @Override
        public Optional<RuleHit> evaluate(TransactionEvent e, Features f) {
            return f.txnCount1m() > maxPerMinute
                    ? Optional.of(new RuleHit(id(), Decision.REVIEW, "txn/1m=" + f.txnCount1m()))
                    : Optional.empty();
        }
    }

    /** One device shared by many accounts: typical account-farming signal. */
    public record DeviceSharing(long maxAccounts) implements Rule {
        @Override
        public String id() {
            return "DEVICE_SHARING";
        }

        @Override
        public Optional<RuleHit> evaluate(TransactionEvent e, Features f) {
            return f.deviceAccountCount() > maxAccounts
                    ? Optional.of(new RuleHit(id(), Decision.REVIEW, "accounts/device=" + f.deviceAccountCount()))
                    : Optional.empty();
        }
    }
}
