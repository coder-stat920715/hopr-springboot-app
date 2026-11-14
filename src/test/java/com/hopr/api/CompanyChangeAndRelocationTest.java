package com.hopr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.DataInitializer;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.User;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompanyChangeAndRelocationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private BookingRepository bookings;

    @Test
    void cognizantKolkataToCapgeminiHyderabadFullFlow() throws Exception {
        String auth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.DEBOJIT_KOL_BIKE_HOST));

        // starts as Cognizant / Kolkata with the green badge
        mvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("KOLKATA"))
                .andExpect(jsonPath("$.corporateBadge.companyName").value("Cognizant"))
                .andExpect(jsonPath("$.corporateBadge.verified").value(true));

        // 1) profile + saved locations
        mvc.perform(put("/api/v1/users/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city":"hyderabad",
                                 "home":{"label":"Gowlidoddy, Hyderabad","lat":17.4262,"lng":78.3500},
                                 "office":{"label":"Capgemini, Nanakramguda, Hyderabad","lat":17.4170,"lng":78.3430}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("HYDERABAD"))
                .andExpect(jsonPath("$.home.label").value("Gowlidoddy, Hyderabad"));

        // 2) company change: badge goes PENDING_REVERIFICATION, then Capgemini after OTP
        mvc.perform(post("/api/v1/users/company-change/initiate").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corporateEmail\":\"debojit.b@capgemini.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devOtp").value("123456"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(jsonPath("$.corporateBadge.status").value("PENDING_REVERIFICATION"))
                .andExpect(jsonPath("$.corporateBadge.verified").value(false));

        mvc.perform(post("/api/v1/users/company-change/verify").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/company-change/verify").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateBadge.companyName").value("Capgemini"))
                .andExpect(jsonPath("$.corporateBadge.badgeColor").value("GREEN"))
                .andExpect(jsonPath("$.corporateEmail").value("debojit.b@capgemini.com"));

        // 3) commute schedule migration
        mvc.perform(post("/api/v1/rides/migrate-city-schedules").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedTemplates").value(2))
                .andExpect(jsonPath("$.newTemplates.length()").value(2))
                .andExpect(jsonPath("$.newTemplates[0].city").value("HYDERABAD"))
                .andExpect(jsonPath("$.newTemplates[0].start.label").value(containsString("Gowlidoddy")))
                .andExpect(jsonPath("$.newTemplates[1].end.label").value(containsString("Gowlidoddy")))
                .andExpect(jsonPath("$.generatedRides").isNumber());

        // old Kolkata templates archived, old pending request from Ananya cancelled
        mvc.perform(get("/api/v1/rides/templates").header("Authorization", auth))
                .andExpect(jsonPath("$[?(@.active==true)].city").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("HYDERABAD"))));
        User ananya = users.findByPhone(DataInitializer.ANANYA_KOL_GUEST).orElseThrow();
        List<Booking> ananyaBookings = bookings.findByGuestIdOrderByCreatedAtDesc(ananya.getId());
        assertThat(ananyaBookings).isNotEmpty();
        assertThat(ananyaBookings).allMatch(b -> b.getStatus() == BookingStatus.CANCELLED);
    }

    @Test
    void supportCanResetLockedCompanyAndNonAdminCannot() throws Exception {
        User target = users.findByPhone(DataInitializer.SUBHAJIT_KOL_CAR_HOST).orElseThrow();
        String adminAuth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.ADMIN));
        String userAuth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.ARJUN_HYD_GUEST));
        String body = "{\"userId\":" + target.getId() + ",\"reason\":\"Organisation field locked\"}";

        mvc.perform(post("/api/v1/support/company-reset").header("Authorization", userAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/support/company-reset").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateBadge.status").value("NONE"))
                .andExpect(jsonPath("$.corporateBadge.verified").value(false));

        // user can now verify a brand new employer from scratch
        String subhajitAuth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.SUBHAJIT_KOL_CAR_HOST));
        mvc.perform(post("/api/v1/users/corporate-email/send-otp").header("Authorization", subhajitAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"corporateEmail\":\"subhajit@tcs.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/corporate-email/verify").header("Authorization", subhajitAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateBadge.companyName").value("TCS"));
    }

    @Test
    void nonAllowListedDomainIsRejected() throws Exception {
        String auth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.ANANYA_KOL_GUEST));
        mvc.perform(post("/api/v1/users/company-change/initiate").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"corporateEmail\":\"x@gmail.com\"}"))
                .andExpect(status().isBadRequest());
    }
}
