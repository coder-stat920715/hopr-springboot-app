package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.BookingDtos.BookingAction;
import com.hopr.dto.BookingDtos.BookingRequest;
import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.RideRepository;
import com.hopr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final RideRepository rides;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final RideMatchingService matching;
    private final WalletService wallets;
    private final NotificationService notifications;
    private final DtoMapper mapper;
    private final HoprProperties props;
    private final Clock clock;

    /** Guest requests a seat. Nothing is reserved yet: the host must accept first. */
    @Transactional
    public BookingResponse request(Long guestId, BookingRequestCommand cmd) {
        User guest = users.findById(guestId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Ride ride = rides.findById(cmd.rideId())
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + cmd.rideId()));
        if (ride.getHost().getId().equals(guestId)) {
            throw new BadRequestException("You cannot book your own ride");
        }
        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Ride is not open for booking (status: " + ride.getStatus() + ")");
        }
        if (ride.getSeatsLeft() <= 0) {
            throw new ConflictException("No seats left on this ride");
        }
        if (bookings.existsByRideIdAndGuestIdAndStatusIn(ride.getId(), guestId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
            throw new ConflictException("You already have an active request for this ride");
        }
        RideMatchingService.MatchDetail m = matching.evaluate(ride, cmd.pickup(), cmd.drop())
                .orElseThrow(() -> new BadRequestException(
                        "This ride does not serve your pickup/drop points (route mismatch or too far to walk)"));
        BigDecimal fare = m.fare().discounted();
        if (wallets.getOrCreate(guestId).getBalance().compareTo(fare) < 0) {
            throw new BadRequestException("Insufficient Hopr wallet balance. Required: " + fare + ". Please top up.");
        }
        Booking b = bookings.save(Booking.builder()
                .ride(ride)
                .guest(guest)
                .status(BookingStatus.PENDING)
                .pickup(cmd.pickup())
                .dropPoint(cmd.drop())
                .pickupWalkMeters(m.pickupWalkMeters())
                .sharedRideKm(m.sharedRideKm())
                .dropWalkMeters(m.dropWalkMeters())
                .matchPercentage(m.matchPercentage())
                .pickupWindowStart(m.pickupWindowStart())
                .pickupWindowEnd(m.pickupWindowEnd())
                .standardFare(m.fare().standard())
                .discountedFare(fare)
                .build());
        notifications.notifyUser(ride.getHost().getId(), guest.getFullName() + " requested a seat on ride #" + ride.getId());
        return mapper.booking(b);
    }

    /** Command object so the service does not depend on web DTO validation annotations. */
    public record BookingRequestCommand(Long rideId, com.hopr.entity.GeoPoint pickup, com.hopr.entity.GeoPoint drop) {
        public static BookingRequestCommand from(BookingRequest r) {
            return new BookingRequestCommand(r.rideId(), r.pickup().toGeoPoint(), r.drop().toGeoPoint());
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> hostRequests(Long hostId, Long rideId) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
        if (!ride.getHost().getId().equals(hostId)) {
            throw new ForbiddenException("Only the host can view booking requests");
        }
        return bookings.findByRideIdOrderByCreatedAtAsc(rideId).stream().map(mapper::booking).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long guestId) {
        return bookings.findByGuestIdOrderByCreatedAtDesc(guestId).stream().map(mapper::booking).toList();
    }

    /**
     * Host accepts or rejects a pending request. Accepting decrements the seat count of the {@link Ride}
     * (guarded by its {@code @Version}) and places the discounted fare on escrow hold in the guest's wallet.
     * Concurrent acceptances of the last seat can never overbook: the loser gets an optimistic-lock failure
     * (HTTP 409) or a "no seats left" conflict.
     */
    @Transactional
    public BookingResponse respond(Long hostId, Long bookingId, BookingAction action) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        Ride ride = b.getRide();
        if (!ride.getHost().getId().equals(hostId)) {
            throw new ForbiddenException("Only the host can respond to this request");
        }
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Request already " + b.getStatus());
        }
        if (action == BookingAction.REJECT) {
            b.setStatus(BookingStatus.REJECTED);
            b.setRespondedAt(Instant.now());
            bookings.save(b);
            notifications.notifyUser(b.getGuest().getId(), "Your request for ride #" + ride.getId() + " was declined");
            return mapper.booking(b);
        }
        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Ride is not open for booking (status: " + ride.getStatus() + ")");
        }
        if (ride.getSeatsLeft() <= 0) {
            throw new ConflictException("No seats left on this ride");
        }
        ride.setSeatsLeft(ride.getSeatsLeft() - 1);
        rides.saveAndFlush(ride); // optimistic-lock check happens here

        wallets.holdEscrow(b.getGuest().getId(), b.getDiscountedFare(), b.getId());
        b.setEscrowHeld(true);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setRespondedAt(Instant.now());
        bookings.save(b);
        notifications.notifyUser(b.getGuest().getId(), "Your seat on ride #" + ride.getId() + " is confirmed");
        return mapper.booking(b);
    }

    /** Guest cancels. Late cancellation of a confirmed seat incurs a configurable penalty paid to the host. */
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (!b.getGuest().getId().equals(userId)) {
            throw new ForbiddenException("Only the guest can cancel this booking");
        }
        Ride ride = b.getRide();
        if (b.getStatus() == BookingStatus.PENDING) {
            b.setStatus(BookingStatus.CANCELLED);
            return mapper.booking(bookings.save(b));
        }
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Booking is " + b.getStatus() + " and cannot be cancelled");
        }
        if (ride.getStatus() != RideStatus.SCHEDULED) {
            throw new ConflictException("Trip already started; cancel through support");
        }
        LocalDateTime departure = LocalDateTime.of(ride.getRideDate(), ride.getDepartureTime());
        long minutesLeft = Duration.between(LocalDateTime.now(clock), departure).toMinutes();
        BigDecimal fare = b.getDiscountedFare();
        BigDecimal penalty = BigDecimal.ZERO.setScale(2);
        if (minutesLeft < props.getCancellation().getFreeWindowMinutes()) {
            penalty = fare.multiply(BigDecimal.valueOf(props.getCancellation().getPenaltyPercent()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (b.isEscrowHeld()) {
            if (penalty.signum() > 0) {
                wallets.settleWithPenalty(userId, ride.getHost().getId(), fare, penalty, b.getId());
            } else {
                wallets.refundEscrow(userId, fare, b.getId(), "Refund for cancelled booking #" + b.getId());
            }
        }
        b.setEscrowHeld(false);
        b.setStatus(BookingStatus.CANCELLED);
        ride.setSeatsLeft(Math.min(ride.getTotalSeats(), ride.getSeatsLeft() + 1));
        rides.save(ride);
        bookings.save(b);
        notifications.notifyUser(ride.getHost().getId(), "A passenger cancelled on ride #" + ride.getId());
        return mapper.booking(b);
    }
}
