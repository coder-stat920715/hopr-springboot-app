package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.dto.CommonDtos.CorporateBadge;
import com.hopr.dto.RideDtos.RideResponse;
import com.hopr.dto.RideDtos.TemplateResponse;
import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.dto.VehicleDtos.VehicleResponse;
import com.hopr.entity.Booking;
import com.hopr.entity.CommuteDirection;
import com.hopr.entity.CommuteMode;
import com.hopr.entity.CompanyStatus;
import com.hopr.entity.FareTrend;
import com.hopr.entity.GeoPoint;
import com.hopr.entity.Ride;
import com.hopr.entity.RideTemplate;
import com.hopr.entity.User;
import com.hopr.entity.Vehicle;
import com.hopr.entity.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PricingMapperAndLabelsTest {

    private final HoprProperties props = new HoprProperties();
    private final PricingService pricing = new PricingService(props);
    private final DtoMapper mapper = new DtoMapper();

    @Test
    void pricingRatesMinimumAndDiscount() {
        assertThat(pricing.ratePerKm(VehicleType.BIKE)).isEqualByComparingTo("4.00");
        assertThat(pricing.ratePerKm(VehicleType.CAR)).isEqualByComparingTo("8.00");
        PricingService.Fare small = pricing.quote(new BigDecimal("8.00"), 0.5);
        assertThat(small.standard()).isEqualByComparingTo("10");
        assertThat(small.discounted()).isEqualByComparingTo("8");
        PricingService.Fare big = pricing.quote(new BigDecimal("8.00"), 10);
        assertThat(big.standard()).isEqualByComparingTo("80");
        assertThat(big.discounted()).isEqualByComparingTo("60");
        assertThat(pricing.platformFee(new BigDecimal("60"))).isEqualByComparingTo("3.00");
    }

    @Test
    void hostMatchLabels() {
        assertThat(HostService.matchLabel(95)).isEqualTo("> 90% match");
        assertThat(HostService.matchLabel(85)).isEqualTo("> 80% match");
        assertThat(HostService.matchLabel(75)).isEqualTo("> 70% match");
        assertThat(HostService.matchLabel(65)).isEqualTo("> 60% match");
        assertThat(HostService.matchLabel(50)).isEqualTo("Partial match");
    }

    @Test
    void trendHelper() {
        assertThat(DynamicPricingService.trend(null, BigDecimal.ONE)).isEqualTo(FareTrend.STABLE);
        assertThat(DynamicPricingService.trend(BigDecimal.ONE, null)).isEqualTo(FareTrend.STABLE);
        assertThat(DynamicPricingService.trend(BigDecimal.ONE, BigDecimal.TEN)).isEqualTo(FareTrend.UP);
        assertThat(DynamicPricingService.trend(BigDecimal.TEN, BigDecimal.ONE)).isEqualTo(FareTrend.DOWN);
        assertThat(DynamicPricingService.trend(BigDecimal.TEN, BigDecimal.TEN)).isEqualTo(FareTrend.STABLE);
    }

    @Test
    void dynamicPricingDetourIsCappedAndNeverNegative() {
        DynamicPricingService dyn = new DynamicPricingService(props);
        Ride ride = Ride.builder().departureTime(LocalTime.of(13, 0)).totalSeats(1).seatsLeft(0).build();
        DynamicPricingService.DynamicQuote q = dyn.quote(ride, new BigDecimal("100"), 1500, 1500, 0.01);
        assertThat(q.dynamicFare()).isEqualByComparingTo("108"); // capped at +8 %
        props.getPricing().getDynamic().setOffPeakDiscountPercent(1000);
        Ride empty = Ride.builder().departureTime(LocalTime.of(13, 0)).totalSeats(1).seatsLeft(1).build();
        assertThat(dyn.quote(empty, new BigDecimal("50"), 0, 0, 5).dynamicFare()).isEqualByComparingTo("50");
    }

    @Test
    void badgeStates() {
        User verified = User.builder().corporateVerified(true).companyStatus(CompanyStatus.VERIFIED)
                .verifiedCompanyName("TCS").build();
        CorporateBadge green = mapper.badge(verified);
        assertThat(green.badgeColor()).isEqualTo("GREEN");
        assertThat(green.companyName()).isEqualTo("TCS");
        CorporateBadge amber = mapper.badge(User.builder().companyStatus(CompanyStatus.PENDING_REVERIFICATION).build());
        assertThat(amber.badgeColor()).isEqualTo("AMBER");
        CorporateBadge grey = mapper.badge(User.builder().build());
        assertThat(grey.badgeColor()).isEqualTo("GREY");
        assertThat(grey.verified()).isFalse();
    }

    @Test
    void windowFormatting() {
        assertThat(DtoMapper.window(null, LocalTime.NOON)).isNull();
        assertThat(DtoMapper.window(LocalTime.NOON, null)).isNull();
        assertThat(DtoMapper.window(LocalTime.of(8, 30), LocalTime.of(8, 40))).isEqualTo("08:30 AM - 08:40 AM");
    }

    @Test
    void entityMappings() {
        GeoPoint a = GeoPoint.of("A", 17.0, 78.0);
        GeoPoint b = GeoPoint.of("B", 17.1, 78.1);
        User host = User.builder().id(1L).fullName("Host").phone("+911").city("HYDERABAD").home(a).office(b)
                .commuteMode(CommuteMode.HOST).ratingAverage(4.26).ratingCount(3).build();
        User guest = User.builder().id(2L).fullName("Guest").build();
        Vehicle v = Vehicle.builder().id(9L).owner(host).vehicleType(VehicleType.CAR).make("M").model("S")
                .registrationNumber("TS07AB4821").seats(3).active(true).build();
        Ride r = Ride.builder().id(5L).host(host).vehicle(v).direction(CommuteDirection.HOME_TO_OFFICE).start(a).end(b)
                .rideDate(LocalDate.of(2026, 11, 20)).departureTime(LocalTime.of(8, 30)).totalSeats(3).seatsLeft(2)
                .ratePerKm(new BigDecimal("8")).routeDistanceKm(10).city("HYDERABAD").build();
        RideTemplate t = RideTemplate.builder().id(7L).host(host).vehicle(v).direction(CommuteDirection.HOME_TO_OFFICE)
                .start(a).end(b).departureTime(LocalTime.of(8, 30)).daysOfWeek(Set.of(DayOfWeek.MONDAY)).seats(3)
                .city("HYDERABAD").build();
        Booking bk = Booking.builder().id(11L).ride(r).guest(guest).pickup(a).dropPoint(b).pickupWalkMeters(10)
                .sharedRideKm(5).dropWalkMeters(20).matchPercentage(90).pickupWindowStart(LocalTime.of(8, 35))
                .pickupWindowEnd(LocalTime.of(8, 45)).discountedFare(new BigDecimal("60"))
                .dynamicFare(new BigDecimal("66")).standardFare(new BigDecimal("80")).build();

        UserProfile p = mapper.profile(host);
        assertThat(p.fullName()).isEqualTo("Host");
        assertThat(p.ratingAverage()).isEqualTo(4.3);
        assertThat(p.home().label()).isEqualTo("A");
        VehicleResponse vr = mapper.vehicle(v);
        assertThat(vr.maskedRegistration()).isEqualTo("TS-07-****-4821");
        assertThat(mapper.publicVehicle(v).maskedRegistration()).isEqualTo("TS-07-****-4821");
        RideResponse rr = mapper.ride(r);
        assertThat(rr.seatsLeft()).isEqualTo(2);
        TemplateResponse tr = mapper.template(t);
        assertThat(tr.days()).containsExactly(DayOfWeek.MONDAY);
        BookingResponse br = mapper.booking(bk);
        assertThat(br.dynamicFare()).isEqualByComparingTo("66");
        assertThat(br.fareTrend()).isEqualTo(FareTrend.UP);
        assertThat(br.pickupWindow()).isEqualTo("08:35 AM - 08:45 AM");
        assertThat(bk.effectiveFare()).isEqualByComparingTo("66");
        bk.setDynamicFare(null);
        assertThat(bk.effectiveFare()).isEqualByComparingTo("60");
    }
}
