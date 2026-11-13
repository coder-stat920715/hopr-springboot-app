package com.hopr.service;

import com.hopr.config.AppConfig;
import com.hopr.config.DataInitializer;
import com.hopr.dto.BookingDtos.BookingAction;
import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.dto.RideDtos.CreateRideRequest;
import com.hopr.dto.RideDtos.RideResponse;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.CommuteDirection;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.User;
import com.hopr.entity.Vehicle;
import com.hopr.entity.Wallet;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.RideRepository;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingConcurrencyAndSettlementTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private RideService rideService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private PricingService pricing;
    @Autowired
    private UserRepository users;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private RideRepository rides;
    @Autowired
    private BookingRepository bookings;

    @Test
    void lastSeatCanOnlyBeConfirmedOnceUnderConcurrentAcceptance() throws Exception {
        User ravi = users.findByPhone(DataInitializer.RAVI_HYD_CAR_HOST).orElseThrow();
        Vehicle car = vehicles.findByOwnerIdAndRemovedFalse(ravi.getId()).get(0);
        LocalDate date = LocalDate.now(AppConfig.ZONE).plusDays(3);
        RideResponse created = rideService.createRide(ravi.getId(), new CreateRideRequest(
                CommuteDirection.HOME_TO_OFFICE, car.getId(), LocationDto.from(ravi.getHome()),
                LocationDto.from(ravi.getOffice()), date, LocalTime.of(9, 0), 1));
        assertThat(created.seatsLeft()).isEqualTo(1);

        int guests = 5;
        List<Long> bookingIds = new ArrayList<>();
        List<Long> guestIds = new ArrayList<>();
        for (int i = 0; i < guests; i++) {
            User g = users.save(User.builder().phone("+9190001000" + i).fullName("Concurrent Guest " + i).build());
            walletService.credit(g.getId(), new BigDecimal("500"), com.hopr.entity.TxnType.TOPUP, "test", null);
            BookingResponse b = bookingService.request(g.getId(), new BookingService.BookingRequestCommand(
                    created.id(), ravi.getHome(), ravi.getOffice()));
            bookingIds.add(b.id());
            guestIds.add(g.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(guests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Long bookingId : bookingIds) {
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    bookingService.respond(ravi.getId(), bookingId, BookingAction.ACCEPT);
                    return true;
                } catch (Exception ex) {
                    return false; // optimistic-lock failure or "no seats left"
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int successes = 0;
        for (Future<Boolean> f : results) {
            if (f.get(30, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        Ride ride = rides.findById(created.id()).orElseThrow();
        assertThat(ride.getSeatsLeft()).isZero();
        assertThat(bookings.countByRideIdAndStatus(created.id(), BookingStatus.CONFIRMED)).isEqualTo(1);

        // escrow was held for exactly one guest
        long guestsWithEscrow = guestIds.stream()
                .map(id -> walletService.getOrCreate(id))
                .filter(w -> w.getEscrow().signum() > 0).count();
        assertThat(guestsWithEscrow).isEqualTo(1);
    }

    @Test
    void escrowIsHeldOnAcceptAndSettledToHostOnCompletion() {
        User ravi = users.findByPhone(DataInitializer.RAVI_HYD_CAR_HOST).orElseThrow();
        User arjun = users.findByPhone(DataInitializer.ARJUN_HYD_GUEST).orElseThrow();
        Booking seeded = bookings.findByGuestIdOrderByCreatedAtDesc(arjun.getId()).stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED).findFirst().orElseThrow();
        BigDecimal fare = seeded.getDiscountedFare();
        Long rideId = seeded.getRide().getId();

        Wallet guestBefore = walletService.getOrCreate(arjun.getId());
        Wallet hostBefore = walletService.getOrCreate(ravi.getId());
        BigDecimal guestBalance = guestBefore.getBalance();
        BigDecimal hostBalance = hostBefore.getBalance();
        assertThat(guestBefore.getEscrow()).isEqualByComparingTo(fare); // held when the host accepted

        rideService.startRide(ravi.getId(), rideId);
        rideService.completeRide(ravi.getId(), rideId);

        BigDecimal expectedPayout = fare.subtract(pricing.platformFee(fare));
        Wallet guestAfter = walletService.getOrCreate(arjun.getId());
        Wallet hostAfter = walletService.getOrCreate(ravi.getId());
        assertThat(guestAfter.getEscrow()).isEqualByComparingTo("0");
        assertThat(guestAfter.getBalance()).isEqualByComparingTo(guestBalance);
        assertThat(hostAfter.getBalance()).isEqualByComparingTo(hostBalance.add(expectedPayout));
        assertThat(rides.findById(rideId).orElseThrow().getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(bookings.findById(seeded.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void hostCancellationRefundsEscrowInFull() {
        User sneha = users.findByPhone(DataInitializer.SNEHA_HYD_BIKE_HOST).orElseThrow();
        User arjun = users.findByPhone(DataInitializer.ARJUN_HYD_GUEST).orElseThrow();
        Booking pending = bookings.findByGuestIdOrderByCreatedAtDesc(arjun.getId()).stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING).findFirst().orElseThrow();
        BigDecimal balanceBefore = walletService.getOrCreate(arjun.getId()).getBalance();

        bookingService.respond(sneha.getId(), pending.getId(), BookingAction.ACCEPT);
        Wallet held = walletService.getOrCreate(arjun.getId());
        assertThat(held.getBalance()).isEqualByComparingTo(balanceBefore.subtract(pending.getDiscountedFare()));

        rideService.cancelRide(sneha.getId(), pending.getRide().getId());
        Wallet refunded = walletService.getOrCreate(arjun.getId());
        assertThat(refunded.getBalance()).isEqualByComparingTo(balanceBefore);
        assertThat(refunded.getEscrow()).isEqualByComparingTo(held.getEscrow().subtract(pending.getDiscountedFare()));
    }
}
