package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.FareTrend;
import com.hopr.entity.Ride;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicPricingServiceTest {

    private final HoprProperties props = new HoprProperties();
    private final DynamicPricingService service = new DynamicPricingService(props);

    private Ride ride(LocalTime time, int total, int left) {
        return Ride.builder().departureTime(time).totalSeats(total).seatsLeft(left).build();
    }

    @Test
    void peakHourRideSurges() {
        DynamicPricingService.DynamicQuote q = service.quote(ride(LocalTime.of(8, 30), 1, 1), new BigDecimal("68"), 0, 0, 5.0);
        assertThat(q.trend()).isEqualTo(FareTrend.UP);
        assertThat(q.dynamicFare()).isEqualByComparingTo("75");
    }

    @Test
    void emptyOffPeakRideGetsDiscount() {
        DynamicPricingService.DynamicQuote q = service.quote(ride(LocalTime.of(13, 0), 2, 2), new BigDecimal("68"), 200, 100, 5.0);
        assertThat(q.trend()).isEqualTo(FareTrend.DOWN);
        assertThat(q.dynamicFare()).isLessThan(new BigDecimal("68"));
    }

    @Test
    void lastSeatOfMultiSeatRideAddsScarcity() {
        DynamicPricingService.DynamicQuote q = service.quote(ride(LocalTime.of(13, 0), 3, 1), new BigDecimal("100"), 0, 0, 5.0);
        assertThat(q.trend()).isEqualTo(FareTrend.UP);
        assertThat(q.dynamicFare()).isEqualByComparingTo("105");
    }

    @Test
    void disabledDynamicPricingIsStable() {
        props.getPricing().getDynamic().setEnabled(false);
        DynamicPricingService.DynamicQuote q = service.quote(ride(LocalTime.of(8, 30), 1, 1), new BigDecimal("68"), 0, 0, 5.0);
        assertThat(q.trend()).isEqualTo(FareTrend.STABLE);
        assertThat(q.dynamicFare()).isEqualByComparingTo("68");
    }
}
