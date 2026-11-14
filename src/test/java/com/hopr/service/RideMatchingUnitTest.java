package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.GeoPoint;
import com.hopr.entity.Ride;
import com.hopr.service.RideMatchingService.MatchDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure geometry test: host drives due north along longitude 78.0 from lat 17.00 to 17.10. */
class RideMatchingUnitTest {

    private RideMatchingService service;
    private Ride ride;

    @BeforeEach
    void setUp() {
        HoprProperties props = new HoprProperties();
        service = new RideMatchingService(null, null, new PricingService(props), new DynamicPricingService(props),
                new DtoMapper(), props, Clock.systemDefaultZone());
        ride = Ride.builder()
                .start(GeoPoint.of("Host start", 17.00, 78.00))
                .end(GeoPoint.of("Host end", 17.10, 78.00))
                .routeDistanceKm(12.0)
                .departureTime(LocalTime.of(8, 30))
                .ratePerKm(new BigDecimal("8.00"))
                .build();
    }

    @Test
    void nearbyPickupAndHighMatch() {
        // ~383 m east of the route at t=0.2, destination exactly on the route at t=0.9
        Optional<MatchDetail> m = service.evaluate(ride,
                GeoPoint.of("Guest start", 17.02, 78.0036), GeoPoint.of("Guest end", 17.09, 78.0));
        assertThat(m).isPresent();
        MatchDetail d = m.get();
        assertThat(d.pickupWalkMeters()).isBetween(350, 420);
        assertThat(d.dropWalkMeters()).isLessThan(5);
        assertThat(d.sharedRideKm()).isBetween(8.3, 8.5);
        assertThat(d.matchPercentage()).isGreaterThan(90);
        assertThat(d.pickupWindowEnd()).isEqualTo(d.pickupWindowStart().plusMinutes(10));
        assertThat(d.fare().discounted()).isLessThan(d.fare().standard());
    }

    @Test
    void farPickupIsNotNearby() {
        Optional<MatchDetail> m = service.evaluate(ride,
                GeoPoint.of("Guest start", 17.02, 78.009), GeoPoint.of("Guest end", 17.09, 78.0));
        assertThat(m).isPresent();
        assertThat(m.get().pickupWalkMeters()).isGreaterThan(500);
    }

    @Test
    void oppositeDirectionDoesNotMatch() {
        Optional<MatchDetail> m = service.evaluate(ride,
                GeoPoint.of("Guest start", 17.09, 78.0), GeoPoint.of("Guest end", 17.02, 78.0));
        assertThat(m).isEmpty();
    }

    @Test
    void tooFarToWalkDoesNotMatch() {
        Optional<MatchDetail> m = service.evaluate(ride,
                GeoPoint.of("Guest start", 17.02, 78.05), GeoPoint.of("Guest end", 17.09, 78.0));
        assertThat(m).isEmpty();
    }
}
