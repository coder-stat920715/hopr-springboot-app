package com.hopr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.DataInitializer;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.User;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HostQuickActionIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private BookingRepository bookings;

    private String token(String phone) throws Exception {
        return TestAuth.bearer(TestAuth.login(mvc, mapper, phone));
    }

    private Booking bookingOf(String guestPhone) {
        User guest = users.findByPhone(guestPhone).orElseThrow();
        return bookings.findByGuestIdOrderByCreatedAtDesc(guest.getId()).get(0);
    }

    @Test
    @Order(1)
    void hostSeesQuickActionCardsWithTripComparison() throws Exception {
        String kiran = token(DataInitializer.KIRAN_HYD_BIKE_HOST);
        mvc.perform(patch("/api/v1/users/active-mode").header("Authorization", kiran)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"HOST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("HOST"))
                .andExpect(jsonPath("$.host.pendingQuickActions").value(2))
                .andExpect(jsonPath("$.host.activeSeatsPublished").isNumber());

        mvc.perform(get("/api/v1/host/quick-actions").header("Authorization", kiran))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.label").value("Quick action (2)"))
                .andExpect(jsonPath("$.requests[0].guestName").value("Rajan Dutta"))
                .andExpect(jsonPath("$.requests[0].guestCompanyName").value("TCS"))
                .andExpect(jsonPath("$.requests[0].corporateVerified").value(true))
                .andExpect(jsonPath("$.requests[0].guestPreferredDepartureLabel").value("Wants to leave at 09:00 AM"))
                .andExpect(jsonPath("$.requests[0].hostEstimatedArrivalLabel").value("You'll arrive by 08:34 AM"))
                .andExpect(jsonPath("$.requests[0].hostRideTime").value("08:30 AM"))
                .andExpect(jsonPath("$.requests[0].rideDate").value(matchesPattern("\\d{1,2} [A-Z][a-z]{2}, [A-Z][a-z]+day")))
                .andExpect(jsonPath("$.requests[0].pickupWalkLabel").value("19 m"))
                .andExpect(jsonPath("$.requests[0].sharedRideLabel").value("19.4 km"))
                .andExpect(jsonPath("$.requests[0].dropWalkLabel").value("498 m"))
                .andExpect(jsonPath("$.requests[0].vehicleType").value("BIKE"))
                .andExpect(jsonPath("$.requests[0].baseFare", comparesEqualTo(79.0)))
                .andExpect(jsonPath("$.requests[0].dynamicFare", comparesEqualTo(90.0)))
                .andExpect(jsonPath("$.requests[0].fareTrend").value("UP"))
                .andExpect(jsonPath("$.requests[0].fareLabel").value("\u20B979 \u2192 \u20B990"))
                .andExpect(jsonPath("$.requests[0].matchPercentage").value(83))
                .andExpect(jsonPath("$.requests[0].matchLabel").value("> 80% match"))
                .andExpect(jsonPath("$.requests[1].guestName").value("Neha Sharma"))
                .andExpect(jsonPath("$.requests[1].fareTrend").value("DOWN"));
    }

    @Test
    @Order(2)
    void preAcceptanceChatAndMaskedCall() throws Exception {
        String kiran = token(DataInitializer.KIRAN_HYD_BIKE_HOST);
        String rajan = token(DataInitializer.RAJAN_HYD_GUEST);
        String arjun = token(DataInitializer.ARJUN_HYD_GUEST);
        long id = bookingOf(DataInitializer.RAJAN_HYD_GUEST).getId();

        mvc.perform(post("/api/v1/bookings/" + id + "/chat").header("Authorization", rajan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Can you wait till 9?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderName").value("Rajan Dutta"));
        mvc.perform(post("/api/v1/bookings/" + id + "/chat").header("Authorization", kiran)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"I leave at 8:30, sorry.\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/bookings/" + id + "/chat").header("Authorization", kiran))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].senderName").value("Rajan Dutta"));

        mvc.perform(post("/api/v1/bookings/" + id + "/masked-call").header("Authorization", kiran))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.virtualNumber").value(matchesPattern("^\\+9180\\d{8}$")))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty());

        // someone who is neither host nor guest is rejected; empty messages are invalid
        mvc.perform(post("/api/v1/bookings/" + id + "/chat").header("Authorization", arjun)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings/" + id + "/chat").header("Authorization", rajan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    void hostRejectsOneRequest() throws Exception {
        String kiran = token(DataInitializer.KIRAN_HYD_BIKE_HOST);
        String neha = token(DataInitializer.NEHA_HYD_GUEST);
        long id = bookingOf(DataInitializer.NEHA_HYD_GUEST).getId();
        mvc.perform(patch("/api/v1/bookings/" + id + "/respond").header("Authorization", kiran)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        // chat is closed once the request is rejected
        mvc.perform(post("/api/v1/bookings/" + id + "/chat").header("Authorization", neha)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello?\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    void hostAcceptsRequestAndEscrowIsHeld() throws Exception {
        String kiran = token(DataInitializer.KIRAN_HYD_BIKE_HOST);
        String rajan = token(DataInitializer.RAJAN_HYD_GUEST);
        Booking b = bookingOf(DataInitializer.RAJAN_HYD_GUEST);
        mvc.perform(patch("/api/v1/bookings/" + b.getId() + "/respond").header("Authorization", kiran)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.escrowHeld").value(true))
                .andExpect(jsonPath("$.dynamicFare", comparesEqualTo(90.0)));
        mvc.perform(get("/api/v1/wallet").header("Authorization", rajan))
                .andExpect(jsonPath("$.escrowBalance", comparesEqualTo(90.0)))
                .andExpect(jsonPath("$.balance", comparesEqualTo(910.0)));
        mvc.perform(get("/api/v1/host/quick-actions").header("Authorization", kiran))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.label").value("Quick action (0)"));
        org.assertj.core.api.Assertions.assertThat(bookings.findById(b.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @Order(5)
    void modeSwitchRulesAndGuestDashboard() throws Exception {
        String rajan = token(DataInitializer.RAJAN_HYD_GUEST);
        mvc.perform(patch("/api/v1/users/active-mode").header("Authorization", rajan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"HOST\"}"))
                .andExpect(status().isBadRequest()); // no vehicle registered
        mvc.perform(patch("/api/v1/users/active-mode").header("Authorization", rajan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"GUEST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("GUEST"))
                .andExpect(jsonPath("$.guest.upcomingBookings").isNumber())
                .andExpect(jsonPath("$.guest.walletBalance").isNumber());
    }
}
