package com.example.risk.rule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class RuleFactory {
    private RuleFactory() {
    }

    public static RuleSet build(RuleConfig c, long version) {
        BigDecimal review = c.reviewAmount() != null ? c.reviewAmount() : new BigDecimal("5000");
        BigDecimal reject = c.rejectAmount() != null ? c.rejectAmount() : new BigDecimal("20000");
        long velocity = c.velocityPerMinute() != null ? c.velocityPerMinute() : 10;
        long device = c.deviceAccountLimit() != null ? c.deviceAccountLimit() : 3;
        Set<String> black = c.blacklistedUsers() != null ? c.blacklistedUsers() : Set.of();
        long countries = c.maxCountries5m() != null ? c.maxCountries5m() : 1;
        long ips = c.maxIps1h() != null ? c.maxIps1h() : 5;
        BigDecimal newDeviceAmount = c.newDeviceAmount() != null ? c.newDeviceAmount() : new BigDecimal("2000");
        int rejectScore = c.rejectScore() != null ? c.rejectScore() : 80;

        return new RuleSet(version, rejectScore, List.of(
                new Rules.Blacklist(black),
                new Rules.Amount(review, reject),
                new Rules.Velocity(velocity),
                new Rules.DeviceSharing(device),
                new Rules.GeoHopping(countries),
                new Rules.IpHopping(ips),
                new Rules.NewDeviceLargeAmount(newDeviceAmount)));
    }
}
