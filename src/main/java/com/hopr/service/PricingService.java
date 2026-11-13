package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.VehicleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final HoprProperties props;

    public record Fare(BigDecimal standard, BigDecimal discounted) {
    }

    public BigDecimal ratePerKm(VehicleType type) {
        return type == VehicleType.BIKE ? props.getPricing().getBikeRatePerKm() : props.getPricing().getCarRatePerKm();
    }

    /** Fuel-share fare for the shared distance: standard price and discounted (Hopr) price. */
    public Fare quote(BigDecimal ratePerKm, double sharedKm) {
        BigDecimal standard = ratePerKm.multiply(BigDecimal.valueOf(sharedKm)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal min = BigDecimal.valueOf(props.getPricing().getMinimumFare());
        if (standard.compareTo(min) < 0) {
            standard = min;
        }
        BigDecimal discounted = standard
                .multiply(BigDecimal.valueOf(100 - props.getPricing().getDiscountPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Fare(standard.setScale(2), discounted.setScale(2));
    }

    public BigDecimal platformFee(BigDecimal fare) {
        return fare.multiply(BigDecimal.valueOf(props.getPricing().getPlatformFeePercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
