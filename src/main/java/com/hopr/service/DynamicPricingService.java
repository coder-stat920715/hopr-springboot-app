package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.FareTrend;
import com.hopr.entity.Ride;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * Dynamic / surge fuel-share pricing. Starting from the discounted base fare it adds
 * a peak-hour surcharge, a route-detour surcharge (guest walking vs. shared distance) and a scarcity
 * surcharge when the last seat is left; an empty off-peak ride gets a small discount.
 */
@Service
@RequiredArgsConstructor
public class DynamicPricingService {

    private final HoprProperties props;

    public record DynamicQuote(BigDecimal dynamicFare, FareTrend trend) {
    }

    public DynamicQuote quote(Ride ride, BigDecimal baseFare, int pickupWalkMeters, int dropWalkMeters,
                              double sharedKm) {
        HoprProperties.Dynamic cfg = props.getPricing().getDynamic();
        BigDecimal base = baseFare.setScale(2, RoundingMode.HALF_UP);
        if (!cfg.isEnabled()) {
            return new DynamicQuote(base, FareTrend.STABLE);
        }
        double pct = 0;
        if (isPeak(ride.getDepartureTime())) {
            pct += cfg.getPeakSurchargePercent();
        } else if (ride.getSeatsLeft() == ride.getTotalSeats()) {
            pct -= cfg.getOffPeakDiscountPercent();
        }
        double detourRatio = (pickupWalkMeters + dropWalkMeters) / 1000.0 / Math.max(sharedKm, 0.1);
        pct += Math.min(cfg.getMaxDetourSurchargePercent(), detourRatio * 40.0);
        if (ride.getTotalSeats() > 1 && ride.getSeatsLeft() <= 1) {
            pct += cfg.getScarcitySurchargePercent();
        }
        BigDecimal dynamic = base.multiply(BigDecimal.valueOf(1.0 + pct / 100.0))
                .setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        if (dynamic.signum() <= 0) {
            dynamic = base;
        }
        return new DynamicQuote(dynamic, trend(base, dynamic));
    }

    public static FareTrend trend(BigDecimal base, BigDecimal dynamic) {
        if (base == null || dynamic == null) {
            return FareTrend.STABLE;
        }
        int c = dynamic.compareTo(base);
        return c > 0 ? FareTrend.UP : c < 0 ? FareTrend.DOWN : FareTrend.STABLE;
    }

    static boolean isPeak(LocalTime t) {
        return between(t, LocalTime.of(7, 30), LocalTime.of(10, 30))
                || between(t, LocalTime.of(17, 0), LocalTime.of(20, 30));
    }

    private static boolean between(LocalTime t, LocalTime from, LocalTime to) {
        return !t.isBefore(from) && !t.isAfter(to);
    }
}
