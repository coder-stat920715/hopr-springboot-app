package com.hopr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchingApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    private String tomorrow() {
        return LocalDate.now(com.hopr.config.AppConfig.ZONE).plusDays(1).toString();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/rides/matches")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestSeesHighMatchNearbyPickupCarWithMaskedRegistration() throws Exception {
        String token = TestAuth.login(mvc, mapper, DataInitializer.ARJUN_HYD_GUEST);
        mvc.perform(get("/api/v1/rides/matches").param("date", tomorrow())
                        .header("Authorization", TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].host.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$[0].host.corporateBadge.badgeColor").value("GREEN"))
                .andExpect(jsonPath("$[0].matchPercentage", greaterThan(90)))
                .andExpect(jsonPath("$[0].tags", hasItem("> 90% match")))
                .andExpect(jsonPath("$[0].tags", hasItem("Nearby Pickup")))
                .andExpect(jsonPath("$[0].tags", hasItem("Carry")))
                .andExpect(jsonPath("$[0].vehicle.maskedRegistration").value("TS-07-****-4821"))
                .andExpect(jsonPath("$[0].pickupWalkMeters", greaterThan(0)))
                .andExpect(jsonPath("$[0].pickupWindow").isNotEmpty())
                .andExpect(jsonPath("$[0].discountedFare").isNumber())
                // the bike host is also returned when carOnly is not set
                .andExpect(jsonPath("$[?(@.vehicle.type=='BIKE')]").isNotEmpty());
    }

    @Test
    void carOnlyFilterExcludesTwoWheelers() throws Exception {
        String token = TestAuth.login(mvc, mapper, DataInitializer.ARJUN_HYD_GUEST);
        mvc.perform(get("/api/v1/rides/matches").param("date", tomorrow()).param("carOnly", "true")
                        .header("Authorization", TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicle.type").value("CAR"))
                .andExpect(jsonPath("$[?(@.vehicle.type=='BIKE')]").isEmpty());
    }

    @Test
    void departureTimeWindowFilterApplies() throws Exception {
        String token = TestAuth.login(mvc, mapper, DataInitializer.ARJUN_HYD_GUEST);
        // Ravi departs 08:30, Sneha (bike) 08:45 -> a 10 minute window around 08:30 keeps only Ravi
        mvc.perform(get("/api/v1/rides/matches").param("date", tomorrow()).param("time", "08:30")
                        .param("windowMinutes", "10")
                        .header("Authorization", TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.host.name=='Sneha Reddy')]").isEmpty())
                .andExpect(jsonPath("$[?(@.host.name=='Ravi Kumar')]").isNotEmpty());
    }

    @Test
    void kolkataGuestDoesNotSeeHyderabadRides() throws Exception {
        String token = TestAuth.login(mvc, mapper, DataInitializer.ANANYA_KOL_GUEST);
        mvc.perform(get("/api/v1/rides/matches").param("date", tomorrow())
                        .header("Authorization", TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.host.name=='Debojit Banerjee')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.host.name=='Ravi Kumar')]").isEmpty());
    }
}
