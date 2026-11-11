package com.example.risk.model;

/** Real-time features computed per event. */
public record Features(
        long txnCount1m,
        long txnCount1h,
        long deviceAccountCount,
        long countries5m,       // distinct countries this user transacted from in the last 5 minutes
        long ips1h,             // distinct IPs this user transacted from in the last hour
        boolean newDevice) {    // first time this device is seen for the user (and the user has other devices)

    /** Used when the feature store times out or fails (degraded mode). */
    public static final Features EMPTY = new Features(0, 0, 0, 0, 0, false);

    public Features(long txnCount1m, long txnCount1h, long deviceAccountCount) {
        this(txnCount1m, txnCount1h, deviceAccountCount, 0, 0, false);
    }
}
