package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.dto.RideDtos.MatchCard;
import com.hopr.entity.GeoPoint;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.User;
import com.hopr.entity.VehicleType;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.RideRepository;
import com.hopr.repository.UserRepository;
import com.hopr.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Smart geospatial matching engine.
 * <p>
 * The host route is modelled as the line start -> end. The guest's start and end points are projected
 * onto that line: the first projection is where the host picks the guest up, the second where the host
 * drops the guest. Walking distances are Haversine distances between the guest's points and those
 * projections; the shared ride distance is the part of the host route between the two projections.
 */
@Service
@RequiredArgsConstructor
public class RideMatchingService {

    public static final String TAG_HIGH_MATCH = "> 90% match";
    public static final String TAG_NEARBY_PICKUP = "Nearby Pickup";
    public static final String TAG_CARRY = "Carry";

    private final RideRepository rides;
    private final UserRepository users;
    private final PricingService pricing;
    private final DtoMapper mapper;
    private final HoprProperties props;
    private final Clock clock;

    public record MatchDetail(int matchPercentage, int pickupWalkMeters, double sharedRideKm, int dropWalkMeters,
                              LocalTime pickupWindowStart, LocalTime pickupWindowEnd, GeoPoint pickupPoint,
                              GeoPoint dropPoint, PricingService.Fare fare) {
    }

    public record Criteria(GeoPoint start, GeoPoint end, LocalDate date, LocalTime time, Integer windowMinutes,
                           boolean carOnly, Integer minMatch, String city) {
    }

    /** Evaluates how well a single ride serves the guest's start/end points. */
    public Optional<MatchDetail> evaluate(Ride ride, GeoPoint guestStart, GeoPoint guestEnd) {
        HoprProperties.Matching cfg = props.getMatching();
        GeoPoint hs = ride.getStart();
        GeoPoint he = ride.getEnd();

        GeoUtil.Projection pick = GeoUtil.projectOnSegment(guestStart.getLat(), guestStart.getLng(),
                hs.getLat(), hs.getLng(), he.getLat(), he.getLng());
        GeoUtil.Projection drop = GeoUtil.projectOnSegment(guestEnd.getLat(), guestEnd.getLng(),
                hs.getLat(), hs.getLng(), he.getLat(), he.getLng());

        if (drop.t() - pick.t() <= 0.0) {
            return Optional.empty(); // host travels in the opposite direction (or guest points are reversed)
        }
        double sharedKm = GeoUtil.round2((drop.t() - pick.t()) * ride.getRouteDistanceKm());
        if (sharedKm < 0.3) {
            return Optional.empty();
        }
        int pickupWalk = (int) Math.round(GeoUtil.haversineMeters(guestStart.getLat(), guestStart.getLng(),
                pick.lat(), pick.lng()));
        int dropWalk = (int) Math.round(GeoUtil.haversineMeters(guestEnd.getLat(), guestEnd.getLng(),
                drop.lat(), drop.lng()));
        if (pickupWalk > cfg.getMaxWalkMeters() || dropWalk > cfg.getMaxWalkMeters()) {
            return Optional.empty();
        }

        double match = 100.0 * sharedKm / (sharedKm + (pickupWalk + dropWalk) / 1000.0);
        int matchPct = (int) Math.round(match);

        long minutesToPickup = Math.round(pick.t() * ride.getRouteDistanceKm() / cfg.getAverageSpeedKmph() * 60.0);
        LocalTime windowStart = ride.getDepartureTime().plusMinutes(minutesToPickup);
        LocalTime windowEnd = windowStart.plusMinutes(cfg.getPickupWindowMinutes());

        PricingService.Fare fare = pricing.quote(ride.getRatePerKm(), sharedKm);
        return Optional.of(new MatchDetail(matchPct, pickupWalk, sharedKm, dropWalk, windowStart, windowEnd,
                GeoPoint.of("Host pickup point", pick.lat(), pick.lng()),
                GeoPoint.of("Host drop point", drop.lat(), drop.lng()), fare));
    }

    @Transactional(readOnly = true)
    public List<MatchCard> findMatches(Long guestId, Criteria c) {
        HoprProperties.Matching cfg = props.getMatching();
        User guest = users.findById(guestId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);
        LocalDate date = c.date() != null ? c.date() : today;
        int window = c.windowMinutes() != null ? c.windowMinutes() : cfg.getDefaultWindowMinutes();
        int minMatch = c.minMatch() != null ? c.minMatch() : cfg.getMinMatchPercentage();
        String city = c.city() != null && !c.city().isBlank() ? c.city() : guest.getCity();

        List<MatchCard> cards = new ArrayList<>();
        for (Ride r : rides.findByRideDateAndStatusAndSeatsLeftGreaterThan(date, RideStatus.SCHEDULED, 0)) {
            if (r.getHost().getId().equals(guestId)) {
                continue;
            }
            if (city != null && r.getCity() != null && !city.equalsIgnoreCase(r.getCity())) {
                continue;
            }
            if (c.carOnly() && r.getVehicle().getVehicleType() != VehicleType.CAR) {
                continue;
            }
            if (c.time() != null
                    && Math.abs(Duration.between(c.time(), r.getDepartureTime()).toMinutes()) > window) {
                continue;
            }
            if (cfg.isExcludePastRides() && date.equals(today) && r.getDepartureTime().isBefore(nowTime)) {
                continue;
            }
            evaluate(r, c.start(), c.end())
                    .filter(m -> m.matchPercentage() >= minMatch)
                    .ifPresent(m -> cards.add(toCard(r, m)));
        }
        cards.sort(Comparator.comparingInt(MatchCard::matchPercentage).reversed()
                .thenComparingInt(MatchCard::pickupWalkMeters)
                .thenComparing(MatchCard::departureTime));
        return cards;
    }

    private MatchCard toCard(Ride r, MatchDetail m) {
        List<String> tags = new ArrayList<>();
        if (m.matchPercentage() > 90) {
            tags.add(TAG_HIGH_MATCH);
        }
        if (m.pickupWalkMeters() <= props.getMatching().getNearbyPickupMeters()) {
            tags.add(TAG_NEARBY_PICKUP);
        }
        if (r.isLuggageFriendly()) {
            tags.add(TAG_CARRY);
        }
        return new MatchCard(r.getId(), mapper.publicUser(r.getHost()), mapper.publicVehicle(r.getVehicle()),
                r.getDirection(), r.getRideDate(), r.getDepartureTime(), m.matchPercentage(),
                m.pickupWalkMeters(), m.sharedRideKm(), m.dropWalkMeters(),
                DtoMapper.window(m.pickupWindowStart(), m.pickupWindowEnd()),
                m.pickupWindowStart(), m.pickupWindowEnd(), r.getSeatsLeft(),
                m.fare().standard(), m.fare().discounted(), tags,
                LocationDto.from(r.getStart()), LocationDto.from(r.getEnd()),
                LocationDto.from(m.pickupPoint()), LocationDto.from(m.dropPoint()));
    }
}
