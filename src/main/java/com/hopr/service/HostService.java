package com.hopr.service;

import com.hopr.dto.HostDtos.GuestMetrics;
import com.hopr.dto.HostDtos.HostMetrics;
import com.hopr.dto.HostDtos.ModeDashboardResponse;
import com.hopr.dto.HostDtos.QuickActionCard;
import com.hopr.dto.HostDtos.QuickActionsResponse;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.CommuteMode;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.TxnType;
import com.hopr.entity.User;
import com.hopr.entity.Wallet;
import com.hopr.entity.WalletTransaction;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.RideRepository;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import com.hopr.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Host mode: dashboard metrics and the "Quick action" inbox of pending guest requests. */
@Service
@RequiredArgsConstructor
public class HostService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM, EEEE", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final String RUPEE = "\u20B9";
    private static final String ARROW = " \u2192 ";

    private final BookingRepository bookings;
    private final RideRepository rides;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final WalletTransactionRepository txns;
    private final WalletService walletService;
    private final Clock clock;

    @Transactional
    public ModeDashboardResponse switchMode(Long userId, CommuteMode mode) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (mode == CommuteMode.HOST && vehicles.countByOwnerIdAndRemovedFalse(userId) == 0) {
            throw new BadRequestException("Register at least one vehicle before switching to HOST mode");
        }
        u.setCommuteMode(mode);
        users.save(u);
        return dashboard(u);
    }

    @Transactional(readOnly = true)
    public ModeDashboardResponse dashboard(Long userId) {
        return dashboard(users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    private ModeDashboardResponse dashboard(User u) {
        if (u.getCommuteMode() == CommuteMode.HOST) {
            return new ModeDashboardResponse(CommuteMode.HOST, hostMetrics(u.getId()), null);
        }
        long upcoming = 0;
        long pending = 0;
        LocalDate today = LocalDate.now(clock);
        for (Booking b : bookings.findByGuestIdOrderByCreatedAtDesc(u.getId())) {
            boolean future = !b.getRide().getRideDate().isBefore(today) && b.getRide().getStatus() == RideStatus.SCHEDULED;
            if (future && b.getStatus() == BookingStatus.CONFIRMED) {
                upcoming++;
            }
            if (future && b.getStatus() == BookingStatus.PENDING) {
                pending++;
            }
        }
        Wallet w = walletService.getOrCreate(u.getId());
        return new ModeDashboardResponse(CommuteMode.GUEST, null,
                new GuestMetrics(upcoming, pending, w.getBalance(), w.getEscrow()));
    }

    private HostMetrics hostMetrics(Long hostId) {
        List<WalletTransaction> payouts = txns.findByUserIdAndTxnType(hostId, TxnType.PAYOUT);
        BigDecimal recovered = payouts.stream().map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Ride> upcoming = rides.findByHostIdAndRideDateGreaterThanEqualAndStatus(hostId, LocalDate.now(clock), RideStatus.SCHEDULED);
        int seats = upcoming.stream().mapToInt(Ride::getTotalSeats).sum();
        int left = upcoming.stream().mapToInt(Ride::getSeatsLeft).sum();
        return new HostMetrics(recovered.setScale(2), payouts.size(), seats, left, upcoming.size(),
                pendingBookings(hostId).size());
    }

    @Transactional(readOnly = true)
    public QuickActionsResponse quickActions(Long hostId) {
        List<QuickActionCard> cards = new ArrayList<>();
        for (Booking b : pendingBookings(hostId)) {
            cards.add(toCard(b));
        }
        return new QuickActionsResponse(cards.size(), "Quick action (" + cards.size() + ")", cards);
    }

    private List<Booking> pendingBookings(Long hostId) {
        LocalDate today = LocalDate.now(clock);
        List<Booking> list = new ArrayList<>();
        for (Booking b : bookings.findByRideHostIdAndStatusOrderByCreatedAtAsc(hostId, BookingStatus.PENDING)) {
            Ride r = b.getRide();
            if (r.getStatus() == RideStatus.SCHEDULED && !r.getRideDate().isBefore(today)) {
                list.add(b);
            }
        }
        list.sort((a, b) -> {
            int c = a.getRide().getRideDate().compareTo(b.getRide().getRideDate());
            if (c != 0) {
                return c;
            }
            c = a.getRide().getDepartureTime().compareTo(b.getRide().getDepartureTime());
            if (c != 0) {
                return c;
            }
            return Integer.compare(b.getMatchPercentage(), a.getMatchPercentage());
        });
        return list;
    }

    private QuickActionCard toCard(Booking b) {
        Ride r = b.getRide();
        User g = b.getGuest();
        BigDecimal base = b.getDiscountedFare();
        BigDecimal dynamic = b.effectiveFare();
        String company = g.isCorporateVerified() ? g.getVerifiedCompanyName() : null;
        return new QuickActionCard(b.getId(), r.getId(), DATE_FMT.format(r.getRideDate()), r.getRideDate(),
                TIME_FMT.format(r.getDepartureTime()), g.getId(), g.getFullName(), g.getProfilePhotoUrl(),
                company, g.isCorporateVerified(),
                b.getGuestPreferredDepartureTime(),
                b.getGuestPreferredDepartureTime() == null ? null
                        : "Wants to leave at " + TIME_FMT.format(b.getGuestPreferredDepartureTime()),
                b.getPickupWindowStart(),
                b.getPickupWindowStart() == null ? null
                        : "You'll arrive by " + TIME_FMT.format(b.getPickupWindowStart()),
                b.getPickupWalkMeters(), b.getPickupWalkMeters() + " m", b.getSharedRideKm(),
                String.format(Locale.ENGLISH, "%.1f km", b.getSharedRideKm()), r.getVehicle().getVehicleType(),
                b.getDropWalkMeters(), b.getDropWalkMeters() + " m", base, dynamic,
                DynamicPricingService.trend(base, dynamic),
                RUPEE + plain(base) + ARROW + RUPEE + plain(dynamic), b.getMatchPercentage(),
                matchLabel(b.getMatchPercentage()), b.getCreatedAt());
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    static String matchLabel(int pct) {
        if (pct > 90) {
            return "> 90% match";
        }
        if (pct > 80) {
            return "> 80% match";
        }
        if (pct > 70) {
            return "> 70% match";
        }
        if (pct > 60) {
            return "> 60% match";
        }
        return "Partial match";
    }
}
