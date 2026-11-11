package com.example.risk.model;

/** Real-time features computed per event. */
public record Features(long txnCount1m, long txnCount1h, long deviceAccountCount) {
    /** Used when the feature store times out or fails (degraded mode). */
    public static final Features EMPTY = new Features(0, 0, 0);
}
