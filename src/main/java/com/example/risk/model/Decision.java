package com.example.risk.model;

/** Ordered by severity: the final decision is the most severe rule hit. */
public enum Decision {
    PASS, REVIEW, REJECT;

    public static Decision max(Decision a, Decision b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
