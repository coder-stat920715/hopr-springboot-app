package com.hopr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.AppConfig;
import com.hopr.config.DataInitializer;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import com.hopr.service.RideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VehicleAndRideApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private RideService rideService;

    private ApiSupport api;

    @BeforeEach
    void init() {
        api = new ApiSupport(mvc, mapper);
    }

    private long vehicleOf(String phone) {
        long uid = users.findByPhone(phone).orElseThrow().getId();
        return vehicles.findByOwnerIdAndRemovedFalse(uid).get(0).getId();
    }

    private String day(int plus) {
        return LocalDate.now(AppConfig.ZONE).plusDays(plus).toString();
    }

    @Test
    void vehicleLifecycle() throws Exception {
        String sneha = api.login(DataInitializer.SNEHA_HYD_BIKE_HOST);
        long bike = vehicleOf(DataInitializer.SNEHA_HYD_BIKE_HOST);
        long ravisCar = vehicleOf(DataInitializer.RAVI_HYD_CAR_HOST);

        api.get("/api/v1/vehicles", sneha).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.post("/api/v1/vehicles", sneha, "{\"type\":\"BIKE\",\"make\":\"H\",\"model\":\"A\",\"registrationNumber\":\"ts 08 ef 2231\",\"seats\":1}")
                .andExpect(status().isConflict());
        api.post("/api/v1/vehicles", sneha, "{\"type\":\"BIKE\",\"make\":\"H\",\"model\":\"A\",\"registrationNumber\":\"KA01ZZ0001\",\"seats\":2}")
                .andExpect(status().isBadRequest());
        long car = api.id(api.post("/api/v1/vehicles", sneha,
                "{\"type\":\"CAR\",\"make\":\"Tata\",\"model\":\"Tiago\",\"registrationNumber\":\"MH12ZZ9999\",\"seats\":3,\"luggageFriendly\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(false)));

        api.post("/api/v1/vehicles/" + ravisCar + "/activate", sneha, null).andExpect(status().isNotFound());
        api.post("/api/v1/vehicles/" + car + "/activate", sneha, null).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        api.delete("/api/v1/vehicles/" + bike, sneha).andExpect(status().isConflict()); // used by an active commute template
        api.delete("/api/v1/vehicles/" + car, sneha).andExpect(status().isOk());
        api.get("/api/v1/vehicles", sneha).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].active").value(true));
        api.delete("/api/v1/vehicles/999999", sneha).andExpect(status().isNotFound());
    }

    @Test
    void licenceAndHostingRulesForNewUsers() throws Exception {
        api.login("9666000001");
        String auth = api.login("9666000001");
        String template = "{\"direction\":\"HOME_TO_OFFICE\",\"vehicleId\":%d,\"start\":" + ApiSupport.RAVI_HOME
                + ",\"end\":" + ApiSupport.CAPGEMINI + ",\"departureTime\":\"08:30:00\"%s}";
        api.post("/api/v1/rides/templates", auth, String.format(template, 1L, "")).andExpect(status().isForbidden());
        api.post("/api/v1/vehicles/licence", auth, "{\"licenceNumber\":\"BAD\",\"expiryDate\":\"2032-01-01\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/vehicles/licence", auth, "{\"licenceNumber\":\"KA 01-2021 0012345\",\"expiryDate\":\"2032-01-01\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.licenceVerified").value(true));
        api.post("/api/v1/rides/templates", auth, String.format(template, 999999L, "")).andExpect(status().isNotFound());
        long bike = api.id(api.post("/api/v1/vehicles", auth,
                "{\"type\":\"BIKE\",\"make\":\"TVS\",\"model\":\"J\",\"registrationNumber\":\"KA01AB1234\",\"seats\":1}").andExpect(status().isCreated()));
        api.post("/api/v1/rides/templates", auth, String.format(template, bike, ",\"seats\":2")).andExpect(status().isBadRequest());
        api.post("/api/v1/rides/templates", auth, String.format(template, bike, ",\"days\":[\"MONDAY\",\"FRIDAY\"],\"city\":\"kolkata\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.days.length()").value(2)).andExpect(jsonPath("$.city").value("KOLKATA"));
        api.post("/api/v1/rides", auth, ApiSupport.rideJson(bike, day(-1), "08:00:00", 1)).andExpect(status().isBadRequest());
        api.post("/api/v1/rides", auth, ApiSupport.rideJson(bike, day(2), "08:00:00", 1)).andExpect(status().isCreated());
    }

    @Test
    void rideEditingSwappingAndExecution() throws Exception {
        String subhajit = api.login(DataInitializer.SUBHAJIT_KOL_CAR_HOST);
        String ravi = api.login(DataInitializer.RAVI_HYD_CAR_HOST);
        long veh = vehicleOf(DataInitializer.SUBHAJIT_KOL_CAR_HOST);
        long ravisCar = vehicleOf(DataInitializer.RAVI_HYD_CAR_HOST);

        api.post("/api/v1/rides", subhajit, ApiSupport.rideJson(ravisCar, day(5), "09:00:00", 1)).andExpect(status().isNotFound());
        long rideId = api.id(api.post("/api/v1/rides", subhajit, ApiSupport.rideJson(veh, day(5), "09:00:00", 2)).andExpect(status().isCreated()));
        String path = "/api/v1/rides/" + rideId;

        api.get(path, subhajit).andExpect(status().isOk()).andExpect(jsonPath("$.seatsLeft").value(2));
        api.get("/api/v1/rides/999999", subhajit).andExpect(status().isNotFound());
        api.put(path, ravi, "{\"departureTime\":\"10:00:00\"}").andExpect(status().isForbidden());
        api.put(path, subhajit, "{\"departureTime\":\"10:00:00\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.departureTime").value("10:00:00")).andExpect(jsonPath("$.customized").value(true));
        api.put(path, subhajit, "{\"seats\":4}").andExpect(status().isBadRequest());
        api.put(path, subhajit, "{\"seats\":3}").andExpect(status().isOk()).andExpect(jsonPath("$.totalSeats").value(3));
        api.put(path, subhajit, "{\"vehicleId\":" + veh + "}").andExpect(status().isOk());
        double before = api.read(api.get(path, subhajit)).get("routeDistanceKm").asDouble();
        api.put(path, subhajit, "{\"start\":{\"label\":\"A\",\"lat\":17.40,\"lng\":78.30},\"end\":{\"label\":\"B\",\"lat\":17.50,\"lng\":78.40}}")
                .andExpect(status().isOk());
        assertThat(api.read(api.get(path, subhajit)).get("routeDistanceKm").asDouble()).isNotEqualTo(before);

        // swap: CREATE_RETURN (default time 18:30 for a morning ride), then in-place swap, then CREATE_RETURN (default 08:30)
        api.post(path + "/swap-direction?mode=CREATE_RETURN", subhajit, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.departureTime").value("18:30:00")).andExpect(jsonPath("$.direction").value("OFFICE_TO_HOME"));
        api.post(path + "/swap-direction", subhajit, null).andExpect(status().isOk()).andExpect(jsonPath("$.direction").value("OFFICE_TO_HOME"));
        api.post(path + "/swap-direction?mode=CREATE_RETURN", subhajit, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.departureTime").value("08:30:00"));
        api.post(path + "/swap-direction?mode=CREATE_RETURN&returnTime=20:15", subhajit, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.departureTime").value("20:15:00"));

        api.post(path + "/start", ravi, null).andExpect(status().isForbidden());
        api.post(path + "/complete", subhajit, null).andExpect(status().isConflict());
        api.post(path + "/start", subhajit, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        api.post(path + "/start", subhajit, null).andExpect(status().isConflict());
        api.put(path, subhajit, "{\"departureTime\":\"11:00:00\"}").andExpect(status().isConflict());
        api.post(path + "/swap-direction", subhajit, null).andExpect(status().isConflict());
        api.post(path + "/cancel", subhajit, null).andExpect(status().isConflict());
        api.post(path + "/complete", subhajit, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        api.post(path + "/complete", subhajit, null).andExpect(status().isConflict());

        long other = api.id(api.post("/api/v1/rides", subhajit, ApiSupport.rideJson(veh, day(6), "09:00:00", 1)).andExpect(status().isCreated()));
        api.post("/api/v1/rides/" + other + "/cancel", subhajit, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        api.post("/api/v1/rides/" + other + "/cancel", subhajit, null).andExpect(status().isConflict());

        api.get("/api/v1/rides/my", subhajit).andExpect(status().isOk());
        JsonNode dated = api.read(api.get("/api/v1/rides/my?date=" + day(6), subhajit).andExpect(status().isOk()));
        assertThat(dated.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void templatesPlannerAndArchiving() throws Exception {
        String subhajit = api.login(DataInitializer.SUBHAJIT_KOL_CAR_HOST);
        String ravi = api.login(DataInitializer.RAVI_HYD_CAR_HOST);
        JsonNode list = api.read(api.get("/api/v1/rides/templates", subhajit).andExpect(status().isOk()));
        long templateId = list.get(0).get("id").asLong();
        api.delete("/api/v1/rides/templates/" + templateId, ravi).andExpect(status().isForbidden());
        api.delete("/api/v1/rides/templates/999999", subhajit).andExpect(status().isNotFound());
        api.delete("/api/v1/rides/templates/" + templateId, subhajit).andExpect(status().isOk());
        api.get("/api/v1/rides/templates", subhajit).andExpect(jsonPath("$[0].active").value(false));
        rideService.refreshAllTemplates();
    }

    @Test
    void cityMigrationValidationsAndExplicitRequest() throws Exception {
        // no saved home/office and none in the body
        api.login("9666000002");
        String newbie = api.login("9666000002");
        api.post("/api/v1/rides/migrate-city-schedules", newbie, "{}").andExpect(status().isBadRequest());
        String locs = "\"home\":" + ApiSupport.RAVI_HOME + ",\"office\":" + ApiSupport.CAPGEMINI;
        // locations supplied but no vehicle
        api.post("/api/v1/rides/migrate-city-schedules", newbie, "{" + locs + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("vehicle")));
        // vehicle but no verified licence
        api.post("/api/v1/vehicles", newbie, "{\"type\":\"BIKE\",\"make\":\"T\",\"model\":\"J\",\"registrationNumber\":\"KA05AB1111\",\"seats\":1}")
                .andExpect(status().isCreated());
        api.post("/api/v1/rides/migrate-city-schedules", newbie, "{" + locs + "}").andExpect(status().isForbidden());

        String kiran = api.login(DataInitializer.KIRAN_HYD_BIKE_HOST);
        api.post("/api/v1/rides/migrate-city-schedules", kiran,
                "{\"newCity\":\"Pune\"," + locs + ",\"homeToOfficeTime\":\"07:45:00\",\"officeToHomeTime\":\"17:45:00\",\"days\":[\"MONDAY\"]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.newTemplates[0].city").value("PUNE"))
                .andExpect(jsonPath("$.newTemplates[0].departureTime").value("07:45:00"))
                .andExpect(jsonPath("$.newTemplates[1].departureTime").value("17:45:00"))
                .andExpect(jsonPath("$.newTemplates[0].days.length()").value(1));
    }
}
