package com.hopr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.AppConfig;
import com.hopr.config.DataInitializer;
import com.hopr.repository.CorporateDomainRepository;
import com.hopr.service.RideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers seeding re-runs, the planner job and the two matching switches (exclude-past, dynamic pricing off). */
@SpringBootTest(properties = {"hopr.matching.exclude-past-rides=true", "hopr.pricing.dynamic.enabled=false"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MiscIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private DataInitializer initializer;
    @Autowired
    private CorporateDomainRepository domains;
    @Autowired
    private RideService rideService;

    @Test
    void seedingIsIdempotentAndPlannerRuns() {
        long before = domains.count();
        initializer.run();
        assertThat(domains.count()).isEqualTo(before);
        rideService.refreshAllTemplates();
    }

    @Test
    void pastRidesAreExcludedAndDynamicPricingCanBeDisabled() throws Exception {
        ApiSupport api = new ApiSupport(mvc, mapper);
        String arjun = api.login(DataInitializer.ARJUN_HYD_GUEST);
        String today = LocalDate.now(AppConfig.ZONE).toString();
        LocalTime now = LocalTime.now(AppConfig.ZONE);
        JsonNode today1 = api.read(api.get("/api/v1/rides/matches?date=" + today, arjun).andExpect(status().isOk()));
        for (JsonNode card : today1) {
            assertThat(LocalTime.parse(card.get("departureTime").asText())).isAfterOrEqualTo(now.minusMinutes(1));
        }
        String tomorrow = LocalDate.now(AppConfig.ZONE).plusDays(1).toString();
        JsonNode cards = api.read(api.get("/api/v1/rides/matches?date=" + tomorrow, arjun).andExpect(status().isOk()));
        assertThat(cards.size()).isGreaterThan(0);
        for (JsonNode card : cards) {
            assertThat(card.get("fareTrend").asText()).isEqualTo("STABLE");
            assertThat(card.get("dynamicFare").decimalValue()).isEqualByComparingTo(card.get("discountedFare").decimalValue());
        }
    }
}
