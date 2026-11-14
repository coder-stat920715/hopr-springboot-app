package com.hopr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.DataInitializer;
import com.hopr.entity.User;
import com.hopr.repository.UserRepository;
import com.hopr.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthAndUserApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwtService;

    private ApiSupport api;

    @BeforeEach
    void init() {
        api = new ApiSupport(mvc, mapper);
    }

    private ResultActions send(String phone) throws Exception {
        return api.post("/api/v1/auth/send-otp", null, "{\"phone\":\"" + phone + "\"}");
    }

    private ResultActions verify(String phone, String otp, String name) throws Exception {
        String body = "{\"phone\":\"" + phone + "\",\"otp\":\"" + otp + "\""
                + (name == null ? "" : ",\"fullName\":\"" + name + "\"") + "}";
        return api.post("/api/v1/auth/verify-otp", null, body);
    }

    private JsonNode loginFull(String phone, String name) throws Exception {
        send(phone).andExpect(status().isOk());
        return api.read(verify(phone, "123456", name).andExpect(status().isOk()));
    }

    @Test
    void invalidInputsAreRejected() throws Exception {
        api.post("/api/v1/auth/send-otp", null, "{\"phone\":\"123\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/auth/send-otp", null, "{}").andExpect(status().isBadRequest());
        verify("9222222222", "123456", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No OTP requested")));
        api.post("/api/v1/auth/verify-otp", null, "{\"phone\":\"9222222222\",\"otp\":\"1\"}").andExpect(status().isBadRequest());
    }

    @Test
    void newAndExistingUsersAndNameHandling() throws Exception {
        JsonNode first = loginFull("9333333333", "Meera K");
        org.assertj.core.api.Assertions.assertThat(first.get("newUser").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(first.get("user").get("fullName").asText()).isEqualTo("Meera K");
        JsonNode again = loginFull("9333333333", null);
        org.assertj.core.api.Assertions.assertThat(again.get("newUser").asBoolean()).isFalse();

        JsonNode anon = loginFull("9333333334", null);
        org.assertj.core.api.Assertions.assertThat(anon.get("user").get("fullName").asText()).isEqualTo("Hopr User");
        JsonNode renamed = loginFull("9333333334", "Real Name");
        org.assertj.core.api.Assertions.assertThat(renamed.get("user").get("fullName").asText()).isEqualTo("Real Name");
        JsonNode kept = loginFull("9333333334", "Other Name");
        org.assertj.core.api.Assertions.assertThat(kept.get("user").get("fullName").asText()).isEqualTo("Real Name");
    }

    @Test
    void tooManyWrongAttemptsLocksTheOtp() throws Exception {
        String phone = "9444444441";
        send(phone).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            verify(phone, "000000", null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Incorrect OTP")));
        }
        verify(phone, "123456", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Too many")));
        send(phone).andExpect(status().isOk());
        verify(phone, "123456", null).andExpect(status().isOk());
    }

    @Test
    void refreshTokenRules() throws Exception {
        JsonNode login = loginFull(DataInitializer.ARJUN_HYD_GUEST, null);
        String refresh = login.get("refreshToken").asText();
        String access = login.get("accessToken").asText();
        api.post("/api/v1/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        api.post("/api/v1/auth/refresh", null, "{\"refreshToken\":\"" + access + "\"}").andExpect(status().isUnauthorized());
        api.post("/api/v1/auth/refresh", null, "{\"refreshToken\":\"garbage\"}").andExpect(status().isUnauthorized());

        String ghost = jwtService.createRefreshToken(User.builder().id(987654L).phone("+91").build());
        api.post("/api/v1/auth/refresh", null, "{\"refreshToken\":\"" + ghost + "\"}").andExpect(status().isUnauthorized());

        User inactive = users.save(User.builder().phone("+919888888881").fullName("Gone").active(false).build());
        String disabled = jwtService.createRefreshToken(inactive);
        api.post("/api/v1/auth/refresh", null, "{\"refreshToken\":\"" + disabled + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdates() throws Exception {
        loginFull("9555000002", "Profile User");
        String auth = api.login("9555000002");
        api.put("/api/v1/users/profile", auth, "{\"fullName\":\" \",\"city\":\" \"}").andExpect(status().isOk());
        api.put("/api/v1/users/profile", auth, "{\"fullName\":\"New Name\",\"profilePhotoUrl\":\"http://x/p.png\",\"city\":\"pune\","
                + "\"home\":" + ApiSupport.ARJUN_HOME + ",\"office\":" + ApiSupport.CAPGEMINI + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.city").value("PUNE"))
                .andExpect(jsonPath("$.fullName").value("New Name")).andExpect(jsonPath("$.office.label").value("Capgemini"));
        api.put("/api/v1/users/profile", auth, "{\"commuteMode\":\"HOST\"}").andExpect(status().isBadRequest());
        api.put("/api/v1/users/profile", auth, "{\"commuteMode\":\"GUEST\"}").andExpect(status().isOk());
        api.put("/api/v1/users/profile", auth, "{\"home\":{\"label\":\"x\",\"lat\":200,\"lng\":10}}").andExpect(status().isBadRequest());
        api.get("/api/v1/users/me", auth).andExpect(status().isOk()).andExpect(jsonPath("$.city").value("PUNE"));
    }

    @Test
    void firstTimeCorporateEmailVerification() throws Exception {
        loginFull("9555000001", "Corp User");
        String auth = api.login("9555000001");
        api.post("/api/v1/users/corporate-email/verify", auth, "{\"otp\":\"123456\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/users/corporate-email/send-otp", auth, "{\"corporateEmail\":\"abc\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/users/corporate-email/send-otp", auth, "{\"corporateEmail\":\"me@gmail.com\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/users/corporate-email/send-otp", auth, "{\"corporateEmail\":\"me@tcs.com\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.devOtp").value("123456"));
        api.post("/api/v1/users/corporate-email/verify", auth, "{\"otp\":\"000000\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/users/corporate-email/verify", auth, "{\"otp\":\"123456\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.corporateBadge.companyName").value("TCS"))
                .andExpect(jsonPath("$.companyLocked").value(true))
                .andExpect(jsonPath("$.corporateEmailLocked").value(true));
        api.post("/api/v1/users/corporate-email/send-otp", auth, "{\"corporateEmail\":\"me2@tcs.com\"}").andExpect(status().isBadRequest());
    }

    @Test
    void companyChangeRequestValidations() throws Exception {
        String debojit = api.login(DataInitializer.DEBOJIT_KOL_BIKE_HOST);
        api.post("/api/v1/users/company-change/request", debojit,
                "{\"newCompanyName\":\"Cognizant\",\"newCorporateEmail\":\"d@capgemini.com\",\"newCity\":\"KOLKATA\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("belongs to Capgemini")));
        api.post("/api/v1/users/company-change/request", debojit,
                "{\"newCompanyName\":\"Cognizant\",\"newCorporateEmail\":\"debojit.b@cognizant.com\",\"newCity\":\"KOLKATA\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("already verified")));
        api.post("/api/v1/users/company-change/request", debojit,
                "{\"newCompanyName\":\"Cognizant\",\"newCorporateEmail\":\"d@gmail.com\",\"newCity\":\"KOLKATA\"}")
                .andExpect(status().isBadRequest());
        api.post("/api/v1/users/company-change/verify-otp", debojit, "{\"otp\":\"123456\"}").andExpect(status().isBadRequest());
    }

    @Test
    void legacyCompanyChangeBranches() throws Exception {
        String subhajit = api.login(DataInitializer.SUBHAJIT_KOL_CAR_HOST);
        api.post("/api/v1/users/company-change/verify", subhajit, "{\"otp\":\"123456\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/users/company-change/initiate", subhajit, "{\"corporateEmail\":\"x@gmail.com\"}").andExpect(status().isBadRequest());
        api.post("/api/v1/users/company-change/initiate", subhajit, "{\"corporateEmail\":\"other@cognizant.com\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("already verified")));
    }

    @Test
    void companyChangeToCityWithoutDefaultsArchivesCommutes() throws Exception {
        String ananya = api.login(DataInitializer.ANANYA_KOL_GUEST);
        api.post("/api/v1/users/company-change/request", ananya,
                "{\"newCompanyName\":\"TCS\",\"newCorporateEmail\":\"ananya@tcs.com\",\"newCity\":\"pune\"}").andExpect(status().isOk());
        api.post("/api/v1/users/company-change/verify-otp", ananya, "{\"otp\":\"123456\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedCompany").value("Cognizant"))
                .andExpect(jsonPath("$.profile.city").value("PUNE"))
                .andExpect(jsonPath("$.migration.generatedRides").value(0));
        api.put("/api/v1/users/profile", ananya, "{\"city\":\"MUMBAI\"}").andExpect(status().is(423));
        api.put("/api/v1/users/profile", ananya, "{\"city\":\"pune\"}").andExpect(status().isOk());
    }

    @Test
    void companyChangeForNonHostWithDefaultsDoesNotMigrate() throws Exception {
        String rajan = api.login(DataInitializer.RAJAN_HYD_GUEST);
        api.post("/api/v1/users/company-change/request", rajan,
                "{\"newCompanyName\":\"Capgemini\",\"newCorporateEmail\":\"rajan@capgemini.com\",\"newCity\":\"HYDERABAD\"}")
                .andExpect(status().isOk());
        api.post("/api/v1/users/company-change/verify-otp", rajan, "{\"otp\":\"123456\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.home.label").value("Gowlidoddy, Hyderabad"))
                .andExpect(jsonPath("$.migration").doesNotExist());
    }

    @Test
    void adminCompanyResetWithTicket() throws Exception {
        String neha = api.login(DataInitializer.NEHA_HYD_GUEST);
        String admin = api.login(DataInitializer.ADMIN);
        long ticket = api.id(api.post("/api/v1/support/tickets", neha,
                "{\"category\":\"COMPANY_RESET\",\"subject\":\"Locked\",\"description\":\"Please reset my company\"}")
                .andExpect(status().isCreated()));
        long nehaId = users.findByPhone(DataInitializer.NEHA_HYD_GUEST).orElseThrow().getId();
        api.post("/api/v1/support/company-reset", admin, "{\"userId\":" + nehaId + ",\"reason\":\"locked\",\"ticketId\":" + ticket + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.corporateBadge.status").value("NONE"))
                .andExpect(jsonPath("$.companyLocked").value(false));
        api.get("/api/v1/support/tickets", neha).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
        api.post("/api/v1/support/company-reset", admin, "{\"userId\":" + nehaId + "}").andExpect(status().isOk());
        api.post("/api/v1/support/company-reset", admin, "{\"userId\":" + nehaId + ",\"ticketId\":999999}").andExpect(status().isNotFound());
        api.post("/api/v1/support/company-reset", admin, "{\"userId\":999999}").andExpect(status().isNotFound());
        api.post("/api/v1/support/company-reset", neha, "{\"userId\":" + nehaId + "}").andExpect(status().isForbidden());
    }
}
