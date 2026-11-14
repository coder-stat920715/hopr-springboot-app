package com.hopr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.AppConfig;
import com.hopr.config.DataInitializer;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingTripApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private VehicleRepository vehicles;

    private ApiSupport api;
    private String ravi;
    private String arjun;
    private String rajan;
    private String neha;
    private long raviVehicle;

    @BeforeEach
    void init() throws Exception {
        api = new ApiSupport(mvc, mapper);
        ravi = api.login(DataInitializer.RAVI_HYD_CAR_HOST);
        arjun = api.login(DataInitializer.ARJUN_HYD_GUEST);
        rajan = api.login(DataInitializer.RAJAN_HYD_GUEST);
        neha = api.login(DataInitializer.NEHA_HYD_GUEST);
        raviVehicle = vehicles.findByOwnerIdAndRemovedFalse(userId(DataInitializer.RAVI_HYD_CAR_HOST)).get(0).getId();
    }

    private long userId(String phone) {
        return users.findByPhone(phone).orElseThrow().getId();
    }

    private String day(int plus) {
        return LocalDate.now(AppConfig.ZONE).plusDays(plus).toString();
    }

    private long newRide(int plusDays, int seats) throws Exception {
        return api.id(api.post("/api/v1/rides", ravi, ApiSupport.rideJson(raviVehicle, day(plusDays), "09:00:00", seats))
                .andExpect(status().isCreated()));
    }

    private long request(String auth, long rideId) throws Exception {
        return api.id(api.post("/api/v1/bookings/request", auth, ApiSupport.bookingJson(rideId)).andExpect(status().isCreated()));
    }

    private void respond(String auth, long bookingId, String action, int expected) throws Exception {
        api.patch("/api/v1/bookings/" + bookingId + "/respond", auth, ApiSupport.respondJson(action))
                .andExpect(status().is(expected));
    }

    private BigDecimal balance(String auth) throws Exception {
        return api.read(api.get("/api/v1/wallet", auth)).get("balance").decimalValue();
    }

    @Test
    void completeTripLifecycleWithChatCallLocationAndRatings() throws Exception {
        long rideId = newRide(3, 2);
        long b1 = request(arjun, rideId);
        long b2 = request(rajan, rideId);
        api.post("/api/v1/bookings/request", arjun, ApiSupport.bookingJson(rideId)).andExpect(status().isConflict());

        String neighbour = api.login(DataInitializer.SNEHA_HYD_BIKE_HOST);
        api.get("/api/v1/rides/" + rideId + "/requests", neighbour).andExpect(status().isForbidden());
        api.get("/api/v1/rides/999999/requests", ravi).andExpect(status().isNotFound());
        api.get("/api/v1/rides/" + rideId + "/requests", ravi).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        respond(arjun, b1, "ACCEPT", 403);
        respond(ravi, b1, "ACCEPT", 200);
        respond(ravi, b2, "ACCEPT", 200);
        respond(ravi, b1, "ACCEPT", 409);
        api.post("/api/v1/bookings/request", neha, ApiSupport.bookingJson(rideId)).andExpect(status().isConflict());

        String path = "/api/v1/rides/" + rideId;
        api.put(path, ravi, "{\"seats\":1}").andExpect(status().isConflict());
        api.put(path, ravi, "{\"vehicleId\":" + raviVehicle + "}").andExpect(status().isConflict());
        api.post(path + "/swap-direction", ravi, null).andExpect(status().isConflict());

        // trip chat
        String chat = "/api/v1/trips/" + rideId + "/chat";
        api.post(chat, arjun, "{\"content\":\"On my way\"}").andExpect(status().isCreated());
        api.post(chat, rajan, "{\"content\":\"Me too\"}").andExpect(status().isCreated());
        api.post(chat, arjun, "{\"content\":\"\"}").andExpect(status().isBadRequest());
        api.get(chat, ravi).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        api.get(chat, neha).andExpect(status().isForbidden());

        // masked calls
        String call = "/api/v1/trips/" + rideId + "/masked-call";
        api.post(call, arjun, "{\"targetUserId\":" + userId(DataInitializer.RAVI_HYD_CAR_HOST) + "}").andExpect(status().isOk());
        api.post(call, ravi, "{\"targetUserId\":" + userId(DataInitializer.RAJAN_HYD_GUEST) + "}").andExpect(status().isOk());
        api.post(call, arjun, "{\"targetUserId\":" + userId(DataInitializer.RAJAN_HYD_GUEST) + "}").andExpect(status().isForbidden());
        api.post(call, arjun, "{\"targetUserId\":" + userId(DataInitializer.NEHA_HYD_GUEST) + "}").andExpect(status().isBadRequest());
        api.post(call, arjun, "{\"targetUserId\":" + userId(DataInitializer.ARJUN_HYD_GUEST) + "}").andExpect(status().isBadRequest());

        // live location
        String loc = "/api/v1/trips/" + rideId + "/location";
        api.get(loc, arjun).andExpect(status().isNotFound());
        api.post(loc, ravi, "{\"lat\":17.45,\"lng\":78.36}").andExpect(status().isConflict());
        api.post("/api/v1/rides/" + rideId + "/start", ravi, null).andExpect(status().isOk());
        api.post(loc, arjun, "{\"lat\":17.45,\"lng\":78.36}").andExpect(status().isForbidden());
        api.post(loc, ravi, "{\"lat\":17.45,\"lng\":78.36}").andExpect(status().isOk()).andExpect(jsonPath("$.etaMinutes").isNumber());
        api.post(loc, ravi, "{\"lat\":17.44,\"lng\":78.35,\"speedKmph\":3}").andExpect(status().isOk());
        api.post(loc, ravi, "{\"lat\":17.43,\"lng\":78.35,\"speedKmph\":40}").andExpect(status().isOk());
        api.get(loc, arjun).andExpect(status().isOk()).andExpect(jsonPath("$.lat").value(17.43));
        api.get(loc, neha).andExpect(status().isForbidden());

        // trip started: no late bookings, no cancellations, no rating
        api.post("/api/v1/bookings/" + b1 + "/cancel", arjun, null).andExpect(status().isConflict());
        api.post("/api/v1/bookings/request", neha, ApiSupport.bookingJson(rideId)).andExpect(status().isConflict());
        api.post("/api/v1/trips/" + rideId + "/rate", arjun,
                "{\"rateeId\":" + userId(DataInitializer.RAVI_HYD_CAR_HOST) + ",\"stars\":5}").andExpect(status().isConflict());

        api.post("/api/v1/rides/" + rideId + "/complete", ravi, null).andExpect(status().isOk());
        api.post(call, arjun, "{\"targetUserId\":" + userId(DataInitializer.RAVI_HYD_CAR_HOST) + "}").andExpect(status().isConflict());

        // ratings
        String rate = "/api/v1/trips/" + rideId + "/rate";
        long raviId = userId(DataInitializer.RAVI_HYD_CAR_HOST);
        api.post(rate, arjun, "{\"rateeId\":" + raviId + ",\"stars\":5,\"tags\":[\"Punctual\",\"Friendly\"],\"feedback\":\"Great\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tags.length()").value(2));
        api.post(rate, rajan, "{\"rateeId\":" + raviId + ",\"stars\":4}").andExpect(status().isCreated()).andExpect(jsonPath("$.tags.length()").value(0));
        api.post(rate, arjun, "{\"rateeId\":" + raviId + ",\"stars\":1}").andExpect(status().isConflict());
        api.post(rate, ravi, "{\"rateeId\":" + userId(DataInitializer.ARJUN_HYD_GUEST) + ",\"stars\":4}").andExpect(status().isCreated());
        api.post(rate, arjun, "{\"rateeId\":" + userId(DataInitializer.RAJAN_HYD_GUEST) + ",\"stars\":4}").andExpect(status().isBadRequest());
        api.post(rate, arjun, "{\"rateeId\":" + userId(DataInitializer.ARJUN_HYD_GUEST) + ",\"stars\":4}").andExpect(status().isBadRequest());
        api.post(rate, neha, "{\"rateeId\":" + raviId + ",\"stars\":4}").andExpect(status().isForbidden());
        api.post(rate, arjun, "{\"rateeId\":" + userId(DataInitializer.NEHA_HYD_GUEST) + ",\"stars\":4}").andExpect(status().isForbidden());
        api.post(rate, arjun, "{\"rateeId\":" + raviId + ",\"stars\":9}").andExpect(status().isBadRequest());
        api.post("/api/v1/trips/999999/rate", arjun, "{\"rateeId\":" + raviId + ",\"stars\":5}").andExpect(status().isNotFound());

        api.get("/api/v1/users/me", ravi).andExpect(jsonPath("$.ratingCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        api.get("/api/v1/host/dashboard", ravi).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("HOST"))
                .andExpect(jsonPath("$.host.fuelCostRecovered").isNumber());
        api.get("/api/v1/bookings/my", arjun).andExpect(status().isOk());
    }

    @Test
    void bookingValidationsAndNotFoundBranches() throws Exception {
        long rideId = newRide(4, 1);
        api.post("/api/v1/bookings/request", ravi, ApiSupport.bookingJson(rideId)).andExpect(status().isBadRequest());
        api.post("/api/v1/bookings/request", arjun, ApiSupport.bookingJson(999999)).andExpect(status().isNotFound());
        String ananya = api.login(DataInitializer.ANANYA_KOL_GUEST);
        api.post("/api/v1/bookings/request", ananya, "{\"rideId\":" + rideId + ",\"pickup\":{\"label\":\"far\",\"lat\":22.60,\"lng\":88.40},"
                + "\"drop\":{\"label\":\"far2\",\"lat\":22.58,\"lng\":88.47}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("does not serve")));
        api.login("9777000001");
        String broke = api.login("9777000001");
        api.post("/api/v1/bookings/request", broke, ApiSupport.bookingJson(rideId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Insufficient")));

        api.patch("/api/v1/bookings/999999/respond", ravi, ApiSupport.respondJson("ACCEPT")).andExpect(status().isNotFound());
        api.post("/api/v1/bookings/999999/cancel", arjun, null).andExpect(status().isNotFound());
        api.get("/api/v1/bookings/999999/chat", arjun).andExpect(status().isNotFound());
        api.post("/api/v1/bookings/999999/masked-call", arjun, null).andExpect(status().isNotFound());

        long booking = request(arjun, rideId);
        api.post("/api/v1/bookings/" + booking + "/cancel", ravi, null).andExpect(status().isForbidden());
        respond(ravi, booking, "ACCEPT", 200);
        // the only seat is gone
        api.post("/api/v1/bookings/request", rajan, ApiSupport.bookingJson(rideId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("No seats")));
        api.post("/api/v1/bookings/" + booking + "/cancel", arjun, null).andExpect(status().isOk());
        api.post("/api/v1/bookings/" + booking + "/cancel", arjun, null).andExpect(status().isConflict());
    }

    @Test
    void cancellationsRefundsAndRejections() throws Exception {
        // free cancellation of a confirmed seat
        long rideId = newRide(5, 2);
        BigDecimal before = balance(arjun);
        long b = request(arjun, rideId);
        respond(ravi, b, "ACCEPT", 200);
        assertThat(balance(arjun)).isLessThan(before);
        api.post("/api/v1/bookings/" + b + "/cancel", arjun, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(balance(arjun)).isEqualByComparingTo(before);
        api.get("/api/v1/rides/" + rideId, arjun).andExpect(jsonPath("$.seatsLeft").value(2));

        // rejected requests cannot be cancelled and chat is closed
        long rejected = request(rajan, rideId);
        respond(ravi, rejected, "REJECT", 200);
        api.post("/api/v1/bookings/" + rejected + "/cancel", rajan, null).andExpect(status().isConflict());
        api.post("/api/v1/bookings/" + rejected + "/chat", rajan, "{\"content\":\"hi\"}").andExpect(status().isConflict());
        api.post("/api/v1/bookings/" + rejected + "/masked-call", rajan, null).andExpect(status().isConflict());

        // host cancels the whole ride: confirmed + pending bookings are cancelled, money is refunded
        BigDecimal arjunBefore = balance(arjun);
        long confirmed = request(arjun, rideId);
        long pending = request(neha, rideId);
        respond(ravi, confirmed, "ACCEPT", 200);
        api.post("/api/v1/rides/" + rideId + "/cancel", ravi, null).andExpect(status().isOk());
        assertThat(balance(arjun)).isEqualByComparingTo(arjunBefore);
        api.get("/api/v1/bookings/my", neha).andExpect(jsonPath("$[0].status").value("CANCELLED"));
        api.post("/api/v1/bookings/request", rajan, ApiSupport.bookingJson(rideId)).andExpect(status().isConflict());
        assertThat(pending).isNotEqualTo(confirmed);
    }

    @Test
    void lateCancellationChargesAPenaltyToTheGuest() throws Exception {
        Assumptions.assumeTrue(LocalTime.now(AppConfig.ZONE).isBefore(LocalTime.of(23, 30)));
        LocalTime soon = LocalTime.now(AppConfig.ZONE).plusMinutes(10).withNano(0);
        String time = String.format("%02d:%02d:%02d", soon.getHour(), soon.getMinute(), soon.getSecond());
        long rideId = api.id(api.post("/api/v1/rides", ravi, ApiSupport.rideJson(raviVehicle, day(0), time, 1)).andExpect(status().isCreated()));
        BigDecimal before = balance(arjun);
        JsonNode booking = api.read(api.post("/api/v1/bookings/request", arjun, ApiSupport.bookingJson(rideId)).andExpect(status().isCreated()));
        long id = booking.get("id").asLong();
        BigDecimal fare = booking.get("dynamicFare").decimalValue();
        respond(ravi, id, "ACCEPT", 200);
        api.post("/api/v1/bookings/" + id + "/cancel", arjun, null).andExpect(status().isOk());
        BigDecimal penalty = fare.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
        assertThat(balance(arjun)).isEqualByComparingTo(before.subtract(penalty));
    }

    @Test
    void walletReferralsSupportAndSearchEdgeCases() throws Exception {
        api.login("9777000002");
        String user = api.login("9777000002");
        api.get("/api/v1/wallet", user).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0));
        api.post("/api/v1/wallet/topup", user, "{\"amount\":100}").andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(100));
        api.post("/api/v1/wallet/topup", user, "{\"amount\":0}").andExpect(status().isBadRequest());
        api.post("/api/v1/wallet/topup", user, "{\"amount\":60000}").andExpect(status().isBadRequest());

        String code = users.findByPhone(DataInitializer.ARJUN_HYD_GUEST).orElseThrow().getReferralCode();
        String own = users.findByPhone("+919777000002").orElseThrow().getReferralCode();
        api.post("/api/v1/referrals/apply", user, "{\"code\":\"NOPE12\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/referrals/apply", user, "{\"code\":\"" + own + "\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/referrals/apply", user, "{\"code\":\"" + code + "\"}").andExpect(status().isOk());
        api.post("/api/v1/referrals/apply", user, "{\"code\":\"" + code + "\"}").andExpect(status().isBadRequest());
        api.get("/api/v1/referrals", arjun).andExpect(status().isOk()).andExpect(jsonPath("$.successfulReferrals").value(1));

        long ticket = api.id(api.post("/api/v1/support/tickets", user,
                "{\"category\":\"PAYMENT_QUERY\",\"subject\":\"Refund\",\"description\":\"Where is my refund?\",\"rideId\":1}").andExpect(status().isCreated()));
        api.get("/api/v1/support/tickets", user).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.get("/api/v1/support/tickets/all", user).andExpect(status().isForbidden());
        String admin = api.login(DataInitializer.ADMIN);
        api.get("/api/v1/support/tickets/all", admin).andExpect(status().isOk());
        api.patch("/api/v1/support/tickets/999999/respond", admin, "{\"status\":\"RESOLVED\"}").andExpect(status().isNotFound());
        api.patch("/api/v1/support/tickets/" + ticket + "/respond", admin, "{}").andExpect(status().isOk());
        api.patch("/api/v1/support/tickets/" + ticket + "/respond", admin, "{\"status\":\"RESOLVED\",\"adminResponse\":\"Refunded\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

        // search edge cases
        api.get("/api/v1/rides/matches?direction=OFFICE_TO_HOME&date=" + day(1), arjun).andExpect(status().isOk());
        api.get("/api/v1/rides/matches?city=KOLKATA&date=" + day(1), arjun).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.get("/api/v1/rides/matches", user).andExpect(status().isBadRequest());
        api.get("/api/v1/host/dashboard", arjun).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("GUEST"))
                .andExpect(jsonPath("$.guest.upcomingBookings").isNumber());
    }
}
