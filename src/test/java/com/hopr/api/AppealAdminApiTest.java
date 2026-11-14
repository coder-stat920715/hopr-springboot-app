package com.hopr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopr.config.DataInitializer;
import com.hopr.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "hopr.appeals.upload-dir=target/test-uploads-appeal-admin")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppealAdminApiTest {

    private static final String ISSUE = "I need my company, e-mail and city updated after relocating.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository users;

    private ApiSupport api;
    private String admin;

    @BeforeEach
    void init() throws Exception {
        api = new ApiSupport(mvc, mapper);
        admin = api.login(DataInitializer.ADMIN);
    }

    private String appealJson(String phone, String previous, String city, String type, String issue) {
        return "{\"registeredName\":\"Tester\",\"registeredPhoneNumber\":\"" + phone + "\",\"registeredCity\":\"" + city
                + "\",\"profileType\":\"" + type + "\",\"previousRegisteredPhoneNumber\":\"" + previous
                + "\",\"issueDescription\":\"" + issue + "\"}";
    }

    private long submit(String phone, String previous) throws Exception {
        return api.id(api.post("/api/v1/appeals/submit", null, appealJson(phone, previous, "KOLKATA", "HOST_RIDE_GIVER", ISSUE))
                .andExpect(status().isCreated()));
    }

    private MockMultipartHttpServletRequestBuilder multipartBase() {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/api/v1/appeals/submit");
        builder.param("registeredName", "Tester");
        builder.param("registeredPhoneNumber", "9000000004");
        builder.param("registeredCity", "Kolkata");
        builder.param("profileType", "Guest - Ride Taker");
        builder.param("issueDescription", ISSUE);
        return builder;
    }

    @Test
    void submissionValidations() throws Exception {
        api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "", "Atlantis", "HOST_RIDE_GIVER", ISSUE)).andExpect(status().isBadRequest());
        api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "", "KOLKATA", "driver", ISSUE)).andExpect(status().isBadRequest());
        api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "abc", "KOLKATA", "HOST_RIDE_GIVER", ISSUE)).andExpect(status().isBadRequest());
        api.post("/api/v1/appeals/submit", null, appealJson("+0000000000000", "", "KOLKATA", "HOST_RIDE_GIVER", ISSUE)).andExpect(status().isBadRequest());
        api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "+0000000000000", "KOLKATA", "HOST_RIDE_GIVER", ISSUE)).andExpect(status().isBadRequest());
        api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "", "KOLKATA", "HOST_RIDE_GIVER", "short")).andExpect(status().isBadRequest());
        // long enough for the bean validation but too short after trimming
        api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "", "KOLKATA", "HOST_RIDE_GIVER", "   abc          ")).andExpect(status().isBadRequest());
        api.post("/api/v1/appeals/submit", null, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void multipartSubmissionRules() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/appeals/submit").param("registeredName", "Only name"))
                .andExpect(status().isBadRequest());
        MockMultipartHttpServletRequestBuilder text = multipartBase();
        text.file(new MockMultipartFile("screenshots", "a.txt", "text/plain", new byte[]{1}));
        mvc.perform(text).andExpect(status().isBadRequest());
        MockMultipartHttpServletRequestBuilder many = multipartBase();
        for (int i = 0; i < 6; i++) {
            many.file(new MockMultipartFile("screenshots", "s" + i + ".png", "image/png", new byte[]{1, 2}));
        }
        mvc.perform(many).andExpect(status().isBadRequest());
        MockMultipartHttpServletRequestBuilder withFile = multipartBase();
        withFile.file(new MockMultipartFile("screenshots", "ok.png", "image/png", new byte[]{1, 2}));
        mvc.perform(withFile)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.screenshotAttachments.length()").value(1))
                .andExpect(jsonPath("$.profileTypeLabel").value("Guest - Ride Taker"));
        mvc.perform(multipartBase()).andExpect(status().isCreated()).andExpect(jsonPath("$.screenshotAttachments.length()").value(0));
    }

    @Test
    void statusAndConfigAreAvailableWithoutLogin() throws Exception {
        String ticket = api.read(api.post("/api/v1/appeals/submit", null, appealJson("9000000004", "", "KOLKATA", "HOST_RIDE_GIVER", ISSUE))
                .andExpect(status().isCreated())).get("appealTicketNumber").asText();
        api.get("/api/v1/appeals/status/" + ticket.toLowerCase(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.statusMessage").value(containsString("received")));
        api.get("/api/v1/appeals/status/HPR-APL-1999-9999", null).andExpect(status().isNotFound());
        api.get("/api/v1/appeals/config", null).andExpect(status().isOk()).andExpect(jsonPath("$.minIssueLength").value(10))
                .andExpect(jsonPath("$.maxAttachments").value(5)).andExpect(jsonPath("$.maxFileSizeMb").value(5));
    }

    @Test
    void adminEndpointsRequireTheAdminRole() throws Exception {
        String user = api.login(DataInitializer.ARJUN_HYD_GUEST);
        api.get("/api/v1/admin/appeals", null).andExpect(status().isUnauthorized());
        api.get("/api/v1/admin/appeals", user).andExpect(status().isForbidden());
        api.get("/api/v1/admin/appeals", admin).andExpect(status().isOk());
    }

    @Test
    void reviewRejectionAndConflicts() throws Exception {
        long id = submit("9000000004", "");
        api.get("/api/v1/admin/appeals/" + id, admin).andExpect(status().isOk()).andExpect(jsonPath("$.registeredCity").value("KOLKATA"));
        api.get("/api/v1/admin/appeals/999999", admin).andExpect(status().isNotFound());
        api.post("/api/v1/admin/appeals/999999/resolve", admin, "{\"decision\":\"APPROVED\"}").andExpect(status().isNotFound());

        api.post("/api/v1/admin/appeals/" + id + "/resolve", admin, "{\"decision\":\"UNDER_REVIEW\",\"adminRemarks\":\"Looking\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        api.get("/api/v1/appeals/status/" + api.read(api.get("/api/v1/admin/appeals/" + id, admin)).get("appealTicketNumber").asText(), null)
                .andExpect(jsonPath("$.statusMessage").value(containsString("reviewing")));
        api.post("/api/v1/admin/appeals/" + id + "/resolve", admin, "{\"decision\":\"REJECTED\",\"adminRemarks\":\"No proof\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        api.post("/api/v1/admin/appeals/" + id + "/resolve", admin, "{\"decision\":\"APPROVED\"}").andExpect(status().isConflict());
        api.get("/api/v1/appeals/status/" + api.read(api.get("/api/v1/admin/appeals/" + id, admin)).get("appealTicketNumber").asText(), null)
                .andExpect(jsonPath("$.statusMessage").value(containsString("not approved")));
    }

    @Test
    void approvalVariants() throws Exception {
        // default decision (APPROVED) without any remediation flag
        long plain = submit("9000000004", "");
        api.post("/api/v1/admin/appeals/" + plain + "/resolve", admin, "{\"adminRemarks\":\"ok\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.resolutionActions").value(""));

        // previous number without an account falls back to the registered number
        long fallback = submit("9000000004", "9000000099");
        api.post("/api/v1/admin/appeals/" + fallback + "/resolve", admin,
                        "{\"decision\":\"APPROVED\",\"unlockCompanyAndEmail\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionActions").value(containsString("Company and corporate e-mail unlocked")));

        // previous number with an account: device bindings of that number are released and the city is changed
        long viaPrevious = submit("9000000004", "9038577517");
        api.post("/api/v1/admin/appeals/" + viaPrevious + "/resolve", admin,
                        "{\"decision\":\"APPROVED\",\"unlockDeviceLimit\":true,\"unlockCompanyAndEmail\":true,\"targetCity\":\"Pune\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolutionActions").value(containsString("City set to PUNE")))
                .andExpect(jsonPath("$.resolutionActions").value(containsString("1 binding(s) released")));
        assertThat(users.findByPhone(DataInitializer.SOUPTIK_KOL_HOST).orElseThrow().getCity()).isEqualTo("PUNE");
        assertThat(users.findByPhone(DataInitializer.SOUPTIK_KOL_HOST).orElseThrow().isCompanyLocked()).isFalse();

        // unknown account -> nothing is applied and the appeal stays open
        long unknown = api.id(api.post("/api/v1/appeals/submit", null,
                appealJson("9876500123", "", "PUNE", "GUEST_RIDE_TAKER", ISSUE)).andExpect(status().isCreated()));
        api.post("/api/v1/admin/appeals/" + unknown + "/resolve", admin, "{\"unlockCompanyAndEmail\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("No Hopr account")));
        api.get("/api/v1/admin/appeals/" + unknown, admin).andExpect(jsonPath("$.status").value("SUBMITTED"));

        // unsupported target city
        long badCity = submit("9000000004", "");
        api.post("/api/v1/admin/appeals/" + badCity + "/resolve", admin, "{\"targetCity\":\"Atlantis\"}").andExpect(status().isBadRequest());
        api.get("/api/v1/admin/appeals", admin).andExpect(status().isOk());
    }
}