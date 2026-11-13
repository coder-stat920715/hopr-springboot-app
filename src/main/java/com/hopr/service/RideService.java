package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.RideDtos.CreateRideRequest;
import com.hopr.dto.RideDtos.MigrateRequest;
import com.hopr.dto.RideDtos.MigrateResponse;
import com.hopr.dto.RideDtos.RideResponse;
import com.hopr.dto.RideDtos.TemplateRequest;
import com.hopr.dto.RideDtos.TemplateResponse;
import com.hopr.dto.RideDtos.UpdateRideRequest;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.CommuteDirection;
import com.hopr.entity.GeoPoint;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.RideTemplate;
import com.hopr.entity.User;
import com.hopr.entity.Vehicle;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.RideRepository;
import com.hopr.repository.RideTemplateRepository;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import com.hopr.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    public enum SwapMode {UPDATE, CREATE_RETURN}

    private static final Set<DayOfWeek> WEEKDAYS =
            EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final RideRepository rides;
    private final RideTemplateRepository templates;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final WalletService wallets;
    private final PricingService pricing;
    private final NotificationService notifications;
    private final DtoMapper mapper;
    private final HoprProperties props;
    private final Clock clock;

    // ===================================================================== recurring templates

    @Transactional
    public TemplateResponse createTemplate(Long userId, TemplateRequest req) {
        User host = requireHost(userId);
        Vehicle vehicle = requireVehicle(userId, req.vehicleId());
        int seats = resolveSeats(vehicle, req.seats());
        Set<DayOfWeek> days = req.days() == null || req.days().isEmpty() ? EnumSet.copyOf(WEEKDAYS) : new HashSet<>(req.days());
        RideTemplate t = templates.save(RideTemplate.builder()
                .host(host)
                .vehicle(vehicle)
                .direction(req.direction())
                .start(req.start().toGeoPoint())
                .end(req.end().toGeoPoint())
                .departureTime(req.departureTime())
                .daysOfWeek(days)
                .seats(seats)
                .city(req.city() != null && !req.city().isBlank() ? req.city().trim().toUpperCase(Locale.ROOT) : host.getCity())
                .build());
        generateRides(t, LocalDate.now(clock), props.getPlanner().getHorizonDays());
        return mapper.template(t);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates(Long userId) {
        return templates.findByHostIdOrderByIdAsc(userId).stream().map(mapper::template).toList();
    }

    /** Archives a template and cancels its future rides that have no confirmed passengers. */
    @Transactional
    public void archiveTemplate(Long userId, Long templateId) {
        RideTemplate t = templates.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));
        if (!t.getHost().getId().equals(userId)) {
            throw new ForbiddenException("Not your commute template");
        }
        t.setActive(false);
        templates.save(t);
        for (Ride r : rides.findByTemplateIdAndRideDateGreaterThanEqualAndStatus(
                templateId, LocalDate.now(clock), RideStatus.SCHEDULED)) {
            if (bookings.countByRideIdAndStatus(r.getId(), BookingStatus.CONFIRMED) == 0) {
                cancelRideInternal(r, "Recurring commute removed by host");
            }
        }
    }

    /** Daily job that keeps the planner horizon filled for all active templates. */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void refreshAllTemplates() {
        int total = 0;
        for (RideTemplate t : templates.findByActiveTrue()) {
            total += generateRides(t, LocalDate.now(clock), props.getPlanner().getHorizonDays());
        }
        log.info("Planner generated {} rides", total);
    }

    /** Generates concrete ride instances for the template's weekdays inside the horizon. */
    @Transactional
    public int generateRides(RideTemplate t, LocalDate from, int horizonDays) {
        int count = 0;
        for (int i = 0; i < horizonDays; i++) {
            LocalDate date = from.plusDays(i);
            if (!t.getDaysOfWeek().contains(date.getDayOfWeek())) {
                continue;
            }
            if (rides.existsByTemplateIdAndRideDate(t.getId(), date)) {
                continue;
            }
            rides.save(buildRide(t.getHost(), t.getVehicle(), t.getId(), t.getDirection(), t.getStart(), t.getEnd(),
                    date, t.getDepartureTime(), t.getSeats(), t.getCity(), false));
            count++;
        }
        return count;
    }

    // ============================================================================ single rides

    @Transactional
    public RideResponse createRide(Long userId, CreateRideRequest req) {
        User host = requireHost(userId);
        Vehicle vehicle = requireVehicle(userId, req.vehicleId());
        if (req.date().isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("Ride date cannot be in the past");
        }
        Ride r = rides.save(buildRide(host, vehicle, null, req.direction(), req.start().toGeoPoint(),
                req.end().toGeoPoint(), req.date(), req.departureTime(), resolveSeats(vehicle, req.seats()),
                host.getCity(), false));
        return mapper.ride(r);
    }

    @Transactional(readOnly = true)
    public RideResponse getRide(Long rideId) {
        return mapper.ride(findRide(rideId));
    }

    @Transactional(readOnly = true)
    public List<RideResponse> myRides(Long userId, LocalDate date) {
        List<Ride> list = date == null
                ? rides.findByHostIdOrderByRideDateAscDepartureTimeAsc(userId)
                : rides.findByHostIdAndRideDateOrderByDepartureTimeAsc(userId, date);
        return list.stream().map(mapper::ride).toList();
    }

    /** Edits one dated ride; the recurring master template is never touched. */
    @Transactional
    public RideResponse updateRide(Long userId, Long rideId, UpdateRideRequest req) {
        Ride r = ownedRide(userId, rideId);
        requireScheduled(r);
        long confirmed = bookings.countByRideIdAndStatus(rideId, BookingStatus.CONFIRMED);
        boolean routeOrVehicleChange = req.start() != null || req.end() != null || req.vehicleId() != null;
        if (confirmed > 0 && routeOrVehicleChange) {
            throw new ConflictException("Route and vehicle cannot be changed after passengers are confirmed");
        }
        if (req.vehicleId() != null) {
            Vehicle v = requireVehicle(userId, req.vehicleId());
            r.setVehicle(v);
            r.setRatePerKm(pricing.ratePerKm(v.getVehicleType()));
            r.setLuggageFriendly(v.isLuggageFriendly());
            if (req.seats() == null) {
                r.setTotalSeats(Math.min(r.getTotalSeats(), v.getSeats()));
                r.setSeatsLeft(r.getTotalSeats());
            }
        }
        if (req.start() != null) {
            r.setStart(req.start().toGeoPoint());
        }
        if (req.end() != null) {
            r.setEnd(req.end().toGeoPoint());
        }
        if (req.start() != null || req.end() != null) {
            r.setRouteDistanceKm(routeKm(r.getStart(), r.getEnd()));
        }
        if (req.departureTime() != null) {
            r.setDepartureTime(req.departureTime());
        }
        if (req.seats() != null) {
            int newTotal = resolveSeats(r.getVehicle(), req.seats());
            if (newTotal < confirmed) {
                throw new ConflictException("Seats cannot be fewer than already confirmed passengers (" + confirmed + ")");
            }
            r.setTotalSeats(newTotal);
            r.setSeatsLeft(newTotal - (int) confirmed);
        }
        r.setCustomized(true);
        return mapper.ride(rides.save(r));
    }

    /**
     * One-tap direction swap. UPDATE swaps start/end of this ride in place (only when it has no active
     * bookings); CREATE_RETURN keeps this ride and creates the return trip as a new ride.
     */
    @Transactional
    public RideResponse swapDirection(Long userId, Long rideId, SwapMode mode, LocalTime returnTime) {
        Ride r = ownedRide(userId, rideId);
        requireScheduled(r);
        CommuteDirection flipped = r.getDirection() == CommuteDirection.HOME_TO_OFFICE
                ? CommuteDirection.OFFICE_TO_HOME : CommuteDirection.HOME_TO_OFFICE;
        if (mode == SwapMode.CREATE_RETURN) {
            LocalTime time = returnTime != null ? returnTime
                    : (r.getDirection() == CommuteDirection.HOME_TO_OFFICE ? LocalTime.of(18, 30) : LocalTime.of(8, 30));
            Ride back = buildRide(r.getHost(), r.getVehicle(), null, flipped, r.getEnd().copy(), r.getStart().copy(),
                    r.getRideDate(), time, r.getTotalSeats(), r.getCity(), true);
            return mapper.ride(rides.save(back));
        }
        boolean active = bookings.countByRideIdAndStatus(rideId, BookingStatus.CONFIRMED) > 0
                || bookings.countByRideIdAndStatus(rideId, BookingStatus.PENDING) > 0;
        if (active) {
            throw new ConflictException("Ride has active bookings. Use mode=CREATE_RETURN or cancel the bookings first.");
        }
        GeoPoint oldStart = r.getStart();
        r.setStart(r.getEnd());
        r.setEnd(oldStart);
        r.setDirection(flipped);
        r.setCustomized(true);
        return mapper.ride(rides.save(r));
    }

    // ======================================================================= trip execution

    @Transactional
    public RideResponse startRide(Long userId, Long rideId) {
        Ride r = ownedRide(userId, rideId);
        if (r.getStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Only a SCHEDULED ride can be started (current: " + r.getStatus() + ")");
        }
        for (Booking b : bookings.findByRideIdAndStatus(rideId, BookingStatus.PENDING)) {
            b.setStatus(BookingStatus.REJECTED);
            bookings.save(b);
        }
        r.setStatus(RideStatus.IN_PROGRESS);
        return mapper.ride(rides.save(r));
    }

    /** Completes the trip and automatically settles escrow: guest escrow -> host wallet payout. */
    @Transactional
    public RideResponse completeRide(Long userId, Long rideId) {
        Ride r = ownedRide(userId, rideId);
        if (r.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ConflictException("Only an IN_PROGRESS ride can be completed (current: " + r.getStatus() + ")");
        }
        for (Booking b : bookings.findByRideIdAndStatus(rideId, BookingStatus.CONFIRMED)) {
            BigDecimal fare = b.getDiscountedFare();
            BigDecimal payout = fare.subtract(pricing.platformFee(fare));
            wallets.settleEscrow(b.getGuest().getId(), r.getHost().getId(), fare, payout, b.getId());
            b.setEscrowHeld(false);
            b.setStatus(BookingStatus.COMPLETED);
            bookings.save(b);
            notifications.notifyUser(b.getGuest().getId(), "Trip completed. Please rate your host.");
        }
        for (Booking b : bookings.findByRideIdAndStatus(rideId, BookingStatus.PENDING)) {
            b.setStatus(BookingStatus.REJECTED);
            bookings.save(b);
        }
        r.setStatus(RideStatus.COMPLETED);
        return mapper.ride(rides.save(r));
    }

    @Transactional
    public RideResponse cancelRide(Long userId, Long rideId) {
        Ride r = ownedRide(userId, rideId);
        requireScheduled(r);
        cancelRideInternal(r, "Ride cancelled by host");
        return mapper.ride(r);
    }

    /** Cancels a ride and fully refunds every passenger. */
    @Transactional
    public void cancelRideInternal(Ride r, String reason) {
        for (Booking b : bookings.findByRideIdOrderByCreatedAtAsc(r.getId())) {
            if (b.getStatus() == BookingStatus.CONFIRMED) {
                if (b.isEscrowHeld()) {
                    wallets.refundEscrow(b.getGuest().getId(), b.getDiscountedFare(), b.getId(),
                            "Full refund: " + reason);
                }
                b.setEscrowHeld(false);
                b.setStatus(BookingStatus.CANCELLED);
                bookings.save(b);
                notifications.notifyUser(b.getGuest().getId(), "Your ride #" + r.getId() + " was cancelled: " + reason);
            } else if (b.getStatus() == BookingStatus.PENDING) {
                b.setStatus(BookingStatus.CANCELLED);
                bookings.save(b);
            }
        }
        r.setStatus(RideStatus.CANCELLED);
        rides.save(r);
    }

    // ============================================================ company / city relocation

    /**
     * Archives the user's recurring templates of the old city/office (cancelling their upcoming rides) and
     * generates new recurring weekday templates for the updated home-to-office route.
     */
    @Transactional
    public MigrateResponse migrateCitySchedules(Long userId, MigrateRequest req) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        GeoPoint home = req.home() != null ? req.home().toGeoPoint() : u.getHome();
        GeoPoint office = req.office() != null ? req.office().toGeoPoint() : u.getOffice();
        if (home == null || home.getLat() == null || office == null || office.getLat() == null) {
            throw new BadRequestException("Home and office locations are required (set them in your profile first)");
        }
        String city = req.newCity() != null && !req.newCity().isBlank()
                ? req.newCity().trim().toUpperCase(Locale.ROOT) : u.getCity();

        List<RideTemplate> old = templates.findByHostIdAndActiveTrue(userId);
        Vehicle vehicle = null;
        LocalTime h2o = req.homeToOfficeTime();
        LocalTime o2h = req.officeToHomeTime();
        Set<DayOfWeek> days = req.days() != null && !req.days().isEmpty() ? new HashSet<>(req.days()) : null;
        for (RideTemplate t : old) {
            if (vehicle == null && !t.getVehicle().isRemoved()) {
                vehicle = t.getVehicle();
            }
            if (t.getDirection() == CommuteDirection.HOME_TO_OFFICE && h2o == null) {
                h2o = t.getDepartureTime();
            }
            if (t.getDirection() == CommuteDirection.OFFICE_TO_HOME && o2h == null) {
                o2h = t.getDepartureTime();
            }
            if (days == null) {
                days = new HashSet<>(t.getDaysOfWeek());
            }
        }
        if (vehicle == null) {
            vehicle = vehicles.findFirstByOwnerIdAndActiveTrueAndRemovedFalse(userId)
                    .or(() -> vehicles.findByOwnerIdAndRemovedFalse(userId).stream().findFirst())
                    .orElseThrow(() -> new BadRequestException("Register a vehicle before creating commute schedules"));
        }
        if (!u.isLicenceVerified()) {
            throw new ForbiddenException("Complete driving licence KYC before hosting rides");
        }
        if (h2o == null) {
            h2o = LocalTime.of(8, 30);
        }
        if (o2h == null) {
            o2h = LocalTime.of(18, 30);
        }
        if (days == null) {
            days = EnumSet.copyOf(WEEKDAYS);
        }

        int cancelled = 0;
        LocalDate today = LocalDate.now(clock);
        for (RideTemplate t : old) {
            t.setActive(false);
            templates.save(t);
            for (Ride r : rides.findByTemplateIdAndRideDateGreaterThanEqualAndStatus(t.getId(), today, RideStatus.SCHEDULED)) {
                cancelRideInternal(r, "Host relocated to a new city/office");
                cancelled++;
            }
        }

        int seats = vehicle.getSeats();
        List<TemplateResponse> created = new ArrayList<>();
        int generated = 0;
        RideTemplate morning = templates.save(RideTemplate.builder().host(u).vehicle(vehicle)
                .direction(CommuteDirection.HOME_TO_OFFICE).start(home.copy()).end(office.copy())
                .departureTime(h2o).daysOfWeek(new HashSet<>(days)).seats(seats).city(city).build());
        RideTemplate evening = templates.save(RideTemplate.builder().host(u).vehicle(vehicle)
                .direction(CommuteDirection.OFFICE_TO_HOME).start(office.copy()).end(home.copy())
                .departureTime(o2h).daysOfWeek(new HashSet<>(days)).seats(seats).city(city).build());
        for (RideTemplate t : List.of(morning, evening)) {
            generated += generateRides(t, today, props.getPlanner().getHorizonDays());
            created.add(mapper.template(t));
        }
        return new MigrateResponse(old.size(), cancelled, generated, created);
    }

    // ================================================================================ helpers

    private Ride buildRide(User host, Vehicle vehicle, Long templateId, CommuteDirection direction,
                           GeoPoint start, GeoPoint end, LocalDate date, LocalTime time, int seats,
                           String city, boolean customized) {
        return Ride.builder()
                .host(host)
                .vehicle(vehicle)
                .templateId(templateId)
                .direction(direction)
                .start(start)
                .end(end)
                .rideDate(date)
                .departureTime(time)
                .totalSeats(seats)
                .seatsLeft(seats)
                .ratePerKm(pricing.ratePerKm(vehicle.getVehicleType()))
                .routeDistanceKm(routeKm(start, end))
                .status(RideStatus.SCHEDULED)
                .city(city)
                .luggageFriendly(vehicle.isLuggageFriendly())
                .customized(customized)
                .build();
    }

    private double routeKm(GeoPoint a, GeoPoint b) {
        double meters = GeoUtil.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        return GeoUtil.round2(meters * props.getMatching().getRoadFactor() / 1000.0);
    }

    private int resolveSeats(Vehicle v, Integer requested) {
        int seats = requested != null ? requested : v.getSeats();
        if (seats > v.getSeats()) {
            throw new BadRequestException("Vehicle offers at most " + v.getSeats() + " seat(s)");
        }
        return seats;
    }

    private User requireHost(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!u.isLicenceVerified()) {
            throw new ForbiddenException("Complete driving licence KYC before hosting rides");
        }
        return u;
    }

    private Vehicle requireVehicle(Long userId, Long vehicleId) {
        return vehicles.findByIdAndOwnerIdAndRemovedFalse(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this user"));
    }

    private Ride findRide(Long rideId) {
        return rides.findById(rideId).orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
    }

    private Ride ownedRide(Long userId, Long rideId) {
        Ride r = findRide(rideId);
        if (!r.getHost().getId().equals(userId)) {
            throw new ForbiddenException("Only the host can perform this action");
        }
        return r;
    }

    private void requireScheduled(Ride r) {
        if (r.getStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Ride is " + r.getStatus() + " and can no longer be modified");
        }
    }
}
