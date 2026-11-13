package com.hopr.config;

import com.hopr.dto.BookingDtos.BookingAction;
import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.dto.RideDtos.CreateRideRequest;
import com.hopr.dto.RideDtos.TemplateRequest;
import com.hopr.entity.*;
import com.hopr.repository.CorporateDomainRepository;
import com.hopr.repository.RideRepository;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import com.hopr.service.AuthService;
import com.hopr.service.BookingService;
import com.hopr.service.RideService;
import com.hopr.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds corporate domains, users (Hyderabad + Kolkata), vehicles, recurring commutes, rides,
 * wallets and sample bookings so every API can be exercised immediately.
 * All OTPs in the dev profile are 123456. Demo phones: +919000000000 (admin) ... +919000000007.
 */
@Component
@ConditionalOnProperty(name = "hopr.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    public static final String ADMIN = "+919000000000";
    public static final String RAVI_HYD_CAR_HOST = "+919000000001";
    public static final String SNEHA_HYD_BIKE_HOST = "+919000000002";
    public static final String ARJUN_HYD_GUEST = "+919000000003";
    public static final String DEBOJIT_KOL_BIKE_HOST = "+919000000004";
    public static final String SUBHAJIT_KOL_CAR_HOST = "+919000000005";
    public static final String ANANYA_KOL_GUEST = "+919000000006";

    private final CorporateDomainRepository domains;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final RideRepository rides;
    private final RideService rideService;
    private final BookingService bookingService;
    private final WalletService walletService;
    private final AuthService authService;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (domains.count() > 0) {
            return;
        }
        for (String[] d : new String[][]{{"capgemini.com", "Capgemini"}, {"cognizant.com", "Cognizant"},
                {"tcs.com", "TCS"}, {"99acres.com", "99acres"}}) {
            domains.save(CorporateDomain.builder().domain(d[0]).companyName(d[1]).build());
        }

        // ------------------------------------------------------------------ locations
        GeoPoint capgemini = GeoPoint.of("Capgemini, Nanakramguda, Hyderabad", 17.4170, 78.3430);
        GeoPoint tcsGachibowli = GeoPoint.of("TCS Synergy Park, Gachibowli, Hyderabad", 17.4225, 78.3485);
        GeoPoint candor = GeoPoint.of("Candor TechSpace C1, New Town, Kolkata", 22.5790, 88.4750);

        // ------------------------------------------------------------------ users
        User admin = user(ADMIN, "Hopr Admin", "HYDERABAD", null, null, null, null, false, "0", true);
        User ravi = user(RAVI_HYD_CAR_HOST, "Ravi Kumar", "HYDERABAD",
                GeoPoint.of("Miyapur Metro, Hyderabad", 17.4968, 78.3614), capgemini,
                "Capgemini", "ravi.kumar@capgemini.com", true, "800", false);
        User sneha = user(SNEHA_HYD_BIKE_HOST, "Sneha Reddy", "HYDERABAD",
                GeoPoint.of("Hafeezpet, Hyderabad", 17.4830, 78.3560), tcsGachibowli,
                "TCS", "sneha.reddy@tcs.com", true, "400", false);
        User arjun = user(ARJUN_HYD_GUEST, "Arjun Mehta", "HYDERABAD",
                GeoPoint.of("Kondapur, Hyderabad", 17.4600, 78.3560), capgemini,
                "Capgemini", "arjun.mehta@capgemini.com", false, "1500", false);
        User debojit = user(DEBOJIT_KOL_BIKE_HOST, "Debojit Banerjee", "KOLKATA",
                GeoPoint.of("Ichapur, North 24 Parganas, Kolkata", 22.8006, 88.3716), candor,
                "Cognizant", "debojit.b@cognizant.com", true, "500", false);
        User subhajit = user(SUBHAJIT_KOL_CAR_HOST, "Subhajit Sen", "KOLKATA",
                GeoPoint.of("Birati, Kolkata", 22.6700, 88.4350), candor,
                "Cognizant", "subhajit.sen@cognizant.com", true, "600", false);
        User ananya = user(ANANYA_KOL_GUEST, "Ananya Roy", "KOLKATA",
                GeoPoint.of("Dum Dum Cantonment, Kolkata", 22.6420, 88.4420), candor,
                "Cognizant", "ananya.roy@cognizant.com", false, "1200", false);

        // ---------------------------------------------------------------- vehicles
        Vehicle raviCar = vehicle(ravi, VehicleType.CAR, "Maruti", "Swift", "White", "TS07AB4821", 3, true);
        Vehicle snehaBike = vehicle(sneha, VehicleType.BIKE, "Honda", "Activa", "Grey", "TS08EF2231", 1, false);
        Vehicle debojitBike = vehicle(debojit, VehicleType.BIKE, "Royal Enfield", "Classic 350", "Black", "WB06CD9104", 1, false);
        Vehicle subhajitCar = vehicle(subhajit, VehicleType.CAR, "Hyundai", "i20", "Red", "WB02AB3377", 3, true);

        // ------------------------------------------------- recurring commutes (+ generated rides)
        commute(ravi, raviCar, ravi.getHome(), capgemini, LocalTime.of(8, 30), LocalTime.of(18, 30));
        commute(sneha, snehaBike, sneha.getHome(), tcsGachibowli, LocalTime.of(8, 45), LocalTime.of(18, 15));
        commute(debojit, debojitBike, debojit.getHome(), candor, LocalTime.of(8, 15), LocalTime.of(18, 0));
        commute(subhajit, subhajitCar, subhajit.getHome(), candor, LocalTime.of(8, 30), LocalTime.of(18, 30));

        // ensure concrete rides exist for today and tomorrow even on weekends
        LocalDate today = LocalDate.now(clock);
        for (LocalDate d : List.of(today, today.plusDays(1))) {
            ensureRide(ravi, raviCar, CommuteDirection.HOME_TO_OFFICE, ravi.getHome(), capgemini, d, LocalTime.of(8, 30));
            ensureRide(sneha, snehaBike, CommuteDirection.HOME_TO_OFFICE, sneha.getHome(), tcsGachibowli, d, LocalTime.of(8, 45));
            ensureRide(debojit, debojitBike, CommuteDirection.HOME_TO_OFFICE, debojit.getHome(), candor, d, LocalTime.of(8, 15));
            ensureRide(subhajit, subhajitCar, CommuteDirection.HOME_TO_OFFICE, subhajit.getHome(), candor, d, LocalTime.of(8, 30));
        }

        // ------------------------------------------------------------ sample bookings
        Ride raviRide = findRide(ravi, today.plusDays(1), CommuteDirection.HOME_TO_OFFICE);
        BookingResponse b1 = bookingService.request(arjun.getId(),
                new BookingService.BookingRequestCommand(raviRide.getId(), arjun.getHome(), capgemini));
        bookingService.respond(ravi.getId(), b1.id(), BookingAction.ACCEPT);

        Ride snehaRide = findRide(sneha, today.plusDays(1), CommuteDirection.HOME_TO_OFFICE);
        bookingService.request(arjun.getId(),
                new BookingService.BookingRequestCommand(snehaRide.getId(), arjun.getHome(), capgemini));

        Ride debojitRide = findRide(debojit, today.plusDays(1), CommuteDirection.HOME_TO_OFFICE);
        bookingService.request(ananya.getId(),
                new BookingService.BookingRequestCommand(debojitRide.getId(), ananya.getHome(), candor));

        // a completed trip from yesterday (ratings + settled wallet ledger demo)
        Ride past = rides.save(Ride.builder().host(ravi).vehicle(raviCar)
                .direction(CommuteDirection.HOME_TO_OFFICE).start(ravi.getHome().copy()).end(capgemini.copy())
                .rideDate(today.minusDays(1)).departureTime(LocalTime.of(8, 30)).totalSeats(3).seatsLeft(3)
                .ratePerKm(new BigDecimal("8.00")).routeDistanceKm(11.36).city("HYDERABAD")
                .luggageFriendly(true).build());
        BookingResponse pb = bookingService.request(arjun.getId(),
                new BookingService.BookingRequestCommand(past.getId(), arjun.getHome(), capgemini));
        bookingService.respond(ravi.getId(), pb.id(), BookingAction.ACCEPT);
        rideService.startRide(ravi.getId(), past.getId());
        rideService.completeRide(ravi.getId(), past.getId());

        log.info("Hopr demo data ready. Login with phone +919000000001..06 (admin +919000000000), OTP 123456");
    }

    private User user(String phone, String name, String city, GeoPoint home, GeoPoint office, String company,
                      String corpEmail, boolean licence, String wallet, boolean admin) {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        if (admin) {
            roles.add("ROLE_ADMIN");
        }
        User u = users.save(User.builder()
                .phone(phone).fullName(name).city(city).home(home).office(office)
                .corporateEmail(corpEmail)
                .corporateVerified(company != null)
                .verifiedCompanyName(company)
                .companyStatus(company != null ? CompanyStatus.VERIFIED : CompanyStatus.NONE)
                .licenceVerified(licence)
                .licenceNumber(licence ? "TS0720190012345" : null)
                .licenceExpiry(licence ? LocalDate.now(clock).plusYears(3) : null)
                .commuteMode(licence ? CommuteMode.HOST : CommuteMode.GUEST)
                .roles(roles)
                .referralCode(authService.generateReferralCode())
                .build());
        walletService.getOrCreate(u.getId());
        BigDecimal amount = new BigDecimal(wallet);
        if (amount.signum() > 0) {
            walletService.credit(u.getId(), amount, TxnType.TOPUP, "Welcome balance", null);
        }
        return u;
    }

    private Vehicle vehicle(User owner, VehicleType type, String make, String model, String color,
                            String reg, int seats, boolean luggage) {
        return vehicles.save(Vehicle.builder().owner(owner).vehicleType(type).make(make).model(model)
                .color(color).registrationNumber(reg).seats(seats).luggageFriendly(luggage).active(true).build());
    }

    private void commute(User host, Vehicle v, GeoPoint home, GeoPoint office, LocalTime morning, LocalTime evening) {
        rideService.createTemplate(host.getId(), new TemplateRequest(CommuteDirection.HOME_TO_OFFICE, v.getId(),
                LocationDto.from(home), LocationDto.from(office), morning, null, null, host.getCity()));
        rideService.createTemplate(host.getId(), new TemplateRequest(CommuteDirection.OFFICE_TO_HOME, v.getId(),
                LocationDto.from(office), LocationDto.from(home), evening, null, null, host.getCity()));
    }

    private void ensureRide(User host, Vehicle v, CommuteDirection dir, GeoPoint start, GeoPoint end,
                            LocalDate date, LocalTime time) {
        if (findRideOrNull(host, date, dir) == null) {
            rideService.createRide(host.getId(), new CreateRideRequest(dir, v.getId(),
                    LocationDto.from(start), LocationDto.from(end), date, time, null));
        }
    }

    private Ride findRideOrNull(User host, LocalDate date, CommuteDirection dir) {
        return rides.findByHostIdAndRideDateOrderByDepartureTimeAsc(host.getId(), date).stream()
                .filter(r -> r.getDirection() == dir && r.getStatus() == RideStatus.SCHEDULED)
                .findFirst().orElse(null);
    }

    private Ride findRide(User host, LocalDate date, CommuteDirection dir) {
        Ride r = findRideOrNull(host, date, dir);
        if (r == null) {
            throw new IllegalStateException("Seed ride missing for " + host.getPhone());
        }
        return r;
    }
}
