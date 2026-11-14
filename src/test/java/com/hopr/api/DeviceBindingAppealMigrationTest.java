package com.hopr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.DataInitializer;
import com.hopr.entity.DeviceBindingStatus;
import com.hopr.entity.User;
import com.hopr.entity.UserCorporateHistory;
import com.hopr.repository.UserCorporateHistoryRepository;
import com.hopr.repository.UserDeviceBindingRepository;
import com.hopr.repository.UserRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "hopr.appeals.upload-dir=target/test-uploads")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeviceBindingAppealMigrationTest {

    private static final String DEVICE = DataInitializer.SOUPTIK_DEVICE_ID;
    private static final String SECOND_PHONE = "+919876500001";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private UserCorporateHistoryRepository history;
    @Autowired
    private UserDeviceBindingRepository deviceBindings;

    /** Login through the OTP endpoints, sending the device id in the X-Device-Id header. */
    private String loginOnDevice(String phone) throws Exception {
        mvc.perform(post("/api/v1/auth/send-otp").header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        String body = mvc.perform(post("/api/v1/auth/verify-otp").header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"otp\":\"123456\",\"deviceModel\":\"Pixel\",\"osPlatform\":\"ANDROID\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("accessToken").asText();
    }

    @Test
    @Order(1)
    void secondMobileNumberOnBoundDeviceIsBlocked() throws Exception {
        mvc.perform(post("/api/v1/auth/send-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + SECOND_PHONE + "\",\"deviceId\":\"" + DEVICE + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("DEVICE_ACCOUNT_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value("Device account limit reached"))
                .andExpect(jsonPath("$.description").value(containsString("already linked to another Hopr account")))
                .andExpect(jsonPath("$.ctaButtonText").value("Submit an appeal"))
                .andExpect(jsonPath("$.externalAppealUrl").value("https://survey.zohopublic.in/zs/ekBH9S"))
                .andExpect(jsonPath("$.internalAppealEndpoint").value("/api/v1/appeals/submit"))
                .andExpect(jsonPath("$.appealWebFormUrl").value("/appeal-form.html"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // same rule on verify-otp, with the device id supplied via header
        mvc.perform(post("/api/v1/auth/verify-otp").header("X-Device-Id", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + SECOND_PHONE + "\",\"otp\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_ACCOUNT_LIMIT_REACHED"));
    }

    @Test
    @Order(2)
    void ownerCanStillLoginOnHisOwnDevice() throws Exception {
        String auth = loginOnDevice(DataInitializer.SOUPTIK_KOL_HOST);
        mvc.perform(get("/api/v1/users/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Souptik Tat"))
                .andExpect(jsonPath("$.corporateEmail").value("souptik.tat@cognizant.com"))
                .andExpect(jsonPath("$.companyLocked").value(true))
                .andExpect(jsonPath("$.city").value("KOLKATA"));
    }

    @Test
    @Order(3)
    void lockedCompanyChangeReturns423WithPrefilledAppeal() throws Exception {
        String auth = loginOnDevice(DataInitializer.SOUPTIK_KOL_HOST);
        mvc.perform(post("/api/v1/users/company-change/request").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCompanyName\":\"Capgemini\",\"newCorporateEmail\":\"souptik.tat@capgemini.com\",\"newCity\":\"HYDERABAD\"}"))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.errorCode").value("COMPANY_UPDATE_LOCKED"))
                .andExpect(jsonPath("$.internalAppealEndpoint").value("/api/v1/appeals/submit"))
                .andExpect(jsonPath("$.externalAppealUrl").value("https://survey.zohopublic.in/zs/ekBH9S"))
                .andExpect(jsonPath("$.prefilledAppeal.registeredName").value("Souptik Tat"))
                .andExpect(jsonPath("$.prefilledAppeal.registeredPhoneNumber").value("9038577517"))
                .andExpect(jsonPath("$.prefilledAppeal.registeredCity").value("KOLKATA"));
        // the profile city is locked together with the company
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/profile")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city\":\"HYDERABAD\"}"))
                .andExpect(status().is(423));
    }

    @Test
    @Order(4)
    void appealWithScreenshotAdminApprovalAndFullMigration() throws Exception {
        String souptik = loginOnDevice(DataInitializer.SOUPTIK_KOL_HOST); // token obtained while still bound
        User account = users.findByPhone(DataInitializer.SOUPTIK_KOL_HOST).orElseThrow();

        // 1. public multipart appeal with a screenshot (no JWT)
        MockMultipartFile shot = new MockMultipartFile("screenshots", "device-blocked.png", "image/png", new byte[]{1, 2, 3, 4});
        String created = mvc.perform(multipart("/api/v1/appeals/submit").file(shot)
                        .param("registeredName", "Souptik Tat")
                        .param("registeredPhoneNumber", "9038577517")
                        .param("registeredCity", "Kolkata")
                        .param("profileType", "Host - Ride Giver")
                        .param("issueDescription", "I moved from Cognizant Kolkata to Capgemini Hyderabad and need to change company.")
                        .param("deviceId", DEVICE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appealTicketNumber").value(matchesPattern("^HPR-APL-\\d{4}-0001$")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.registeredCity").value("KOLKATA"))
                .andExpect(jsonPath("$.profileType").value("HOST_RIDE_GIVER"))
                .andExpect(jsonPath("$.screenshotAttachments.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode appeal = mapper.readTree(created);
        long appealId = appeal.get("id").asLong();
        String ticket = appeal.get("appealTicketNumber").asText();

        // validation: too short explanation
        mvc.perform(post("/api/v1/appeals/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registeredPhoneNumber\":\"9038577517\",\"registeredCity\":\"KOLKATA\",\"profileType\":\"GUEST_RIDE_TAKER\",\"issueDescription\":\"short\"}"))
                .andExpect(status().isBadRequest());

        // 2. public status tracking + config
        mvc.perform(get("/api/v1/appeals/status/" + ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(get("/api/v1/appeals/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalFormUrl").value("https://survey.zohopublic.in/zs/ekBH9S"))
                .andExpect(jsonPath("$.maxIssueLength").value(20000))
                .andExpect(jsonPath("$.cities.length()").value(7))
                .andExpect(jsonPath("$.profileTypes[0].label").value("Host - Ride Giver"));

        // 3. only admins can resolve
        String userAuth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.ARJUN_HYD_GUEST));
        String resolveBody = "{\"decision\":\"APPROVED\",\"adminRemarks\":\"Identity verified\",\"unlockDeviceLimit\":true,"
                + "\"unlockCompanyAndEmail\":true,\"targetCity\":\"HYDERABAD\"}";
        mvc.perform(post("/api/v1/admin/appeals/" + appealId + "/resolve").header("Authorization", userAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody))
                .andExpect(status().isForbidden());

        // 4. admin approves with all remediation flags
        String adminAuth = TestAuth.bearer(TestAuth.login(mvc, mapper, DataInitializer.ADMIN));
        mvc.perform(post("/api/v1/admin/appeals/" + appealId + "/resolve").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.resolutionActions").value(containsString("Device limit unlocked")))
                .andExpect(jsonPath("$.resolutionActions").value(containsString("Company and corporate e-mail unlocked")))
                .andExpect(jsonPath("$.resolutionActions").value(containsString("City set to HYDERABAD")));
        mvc.perform(post("/api/v1/admin/appeals/" + appealId + "/resolve").header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON).content(resolveBody))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/appeals/status/" + ticket))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(deviceBindings.findByDeviceIdOrderByIdAsc(DEVICE))
                .allMatch(b -> b.getStatus() == DeviceBindingStatus.UNBOUND_BY_APPEAL);
        User unlocked = users.findByPhone(DataInitializer.SOUPTIK_KOL_HOST).orElseThrow();
        assertThat(unlocked.isCompanyLocked()).isFalse();
        assertThat(unlocked.isCorporateEmailLocked()).isFalse();
        assertThat(unlocked.getCity()).isEqualTo("HYDERABAD");

        // 5. the device is free again: the second mobile number can now log in on it
        loginOnDevice(SECOND_PHONE);

        // 6. employer + city migration: Cognizant (Kolkata) -> Capgemini (Hyderabad)
        mvc.perform(post("/api/v1/users/company-change/request").header("Authorization", souptik)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCompanyName\":\"Cognizant\",\"newCorporateEmail\":\"souptik.tat@capgemini.com\",\"newCity\":\"HYDERABAD\"}"))
                .andExpect(status().isBadRequest()); // e-mail domain does not belong to the stated company
        mvc.perform(post("/api/v1/users/company-change/request").header("Authorization", souptik)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCompanyName\":\"Capgemini\",\"newCorporateEmail\":\"souptik.tat@capgemini.com\",\"newCity\":\"HYDERABAD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devOtp").value("123456"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", souptik))
                .andExpect(jsonPath("$.corporateBadge.status").value("PENDING_REVERIFICATION"));
        mvc.perform(post("/api/v1/users/company-change/verify-otp").header("Authorization", souptik)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"000000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/company-change/verify-otp").header("Authorization", souptik)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedCompany").value("Cognizant"))
                .andExpect(jsonPath("$.profile.corporateBadge.companyName").value("Capgemini"))
                .andExpect(jsonPath("$.profile.corporateBadge.verified").value(true))
                .andExpect(jsonPath("$.profile.corporateEmail").value("souptik.tat@capgemini.com"))
                .andExpect(jsonPath("$.profile.companyLocked").value(true))
                .andExpect(jsonPath("$.profile.city").value("HYDERABAD"))
                .andExpect(jsonPath("$.migration.newTemplates.length()").value(2))
                .andExpect(jsonPath("$.migration.newTemplates[0].start.label").value(containsString("Gowlidoddy")))
                .andExpect(jsonPath("$.migration.newTemplates[0].end.label").value(containsString("Capgemini")));

        // audit history keeps the Cognizant / Kolkata affiliation
        List<UserCorporateHistory> past = history.findByUserIdOrderByArchivedAtDescIdDesc(account.getId());
        assertThat(past).hasSize(1);
        assertThat(past.get(0).getCompanyName()).isEqualTo("Cognizant");
        assertThat(past.get(0).getCorporateEmail()).isEqualTo("souptik.tat@cognizant.com");
        assertThat(past.get(0).getCity()).isEqualTo("KOLKATA");

        // old Kolkata commutes archived, Hyderabad placeholders active
        mvc.perform(get("/api/v1/rides/templates").header("Authorization", souptik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.active==true)].city").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("HYDERABAD"))))
                .andExpect(jsonPath("$[?(@.active==true)]", org.hamcrest.Matchers.hasSize(2)));

        // the profile is locked again after the switch
        mvc.perform(post("/api/v1/users/company-change/request").header("Authorization", souptik)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newCompanyName\":\"TCS\",\"newCorporateEmail\":\"souptik.tat@tcs.com\",\"newCity\":\"MUMBAI\"}"))
                .andExpect(status().is(423));
    }
}
