package com.hopr.dto;

import com.hopr.entity.AppealStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

public final class AppealDtos {
    private AppealDtos() {
    }

    public static final String PHONE_REGEX = "^[+]?[0-9 -]{10,15}$";

    /** JSON body of POST /appeals/submit. City and profile type accept enum names or display labels. */
    public record AppealRequest(
            @Size(max = 200) String registeredName,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "Enter a valid phone number") String registeredPhoneNumber,
            @NotBlank String registeredCity,
            @NotBlank String profileType,
            @Pattern(regexp = "^$|" + PHONE_REGEX, message = "Enter a valid phone number") String previousRegisteredPhoneNumber,
            @NotBlank @Size(min = 10, max = 20000, message = "Explain the issue in 10 to 20000 characters") String issueDescription,
            String deviceId) {
    }

    /** multipart/form-data variant (same fields + optional screenshots). */
    @Getter
    @Setter
    public static class AppealForm {
        @Size(max = 200)
        private String registeredName;
        @NotBlank
        @Pattern(regexp = PHONE_REGEX, message = "Enter a valid phone number")
        private String registeredPhoneNumber;
        @NotBlank
        private String registeredCity;
        @NotBlank
        private String profileType;
        @Pattern(regexp = "^$|" + PHONE_REGEX, message = "Enter a valid phone number")
        private String previousRegisteredPhoneNumber;
        @NotBlank
        @Size(min = 10, max = 20000, message = "Explain the issue in 10 to 20000 characters")
        private String issueDescription;
        private String deviceId;
        private List<MultipartFile> screenshots;

        public AppealRequest toRequest() {
            return new AppealRequest(registeredName, registeredPhoneNumber, registeredCity, profileType,
                    previousRegisteredPhoneNumber, issueDescription, deviceId);
        }
    }

    public enum AppealDecision {APPROVED, REJECTED, UNDER_REVIEW}

    public record ResolveAppealRequest(AppealDecision decision, @Size(max = 2000) String adminRemarks,
                                       boolean unlockDeviceLimit, boolean unlockCompanyAndEmail,
                                       String targetCity) {
    }

    public record AppealResponse(Long id, String appealTicketNumber, String registeredName,
                                 String registeredPhoneNumber, String registeredCity, String profileType,
                                 String profileTypeLabel, String previousRegisteredPhoneNumber, String deviceId,
                                 String issueDescription, List<String> screenshotAttachments, AppealStatus status,
                                 String adminRemarks, String resolutionActions, Instant createdAt,
                                 Instant resolvedAt) {
    }

    public record AppealStatusResponse(String appealTicketNumber, AppealStatus status, String statusMessage,
                                       String adminRemarks, Instant createdAt, Instant resolvedAt) {
    }

    public record Option(String value, String label) {
    }

    public record AppealConfigResponse(String formTitle, String externalFormUrl, String submitEndpoint,
                                       String statusEndpoint, int minIssueLength, int maxIssueLength,
                                       int maxAttachments, long maxFileSizeMb, List<Option> cities,
                                       List<Option> profileTypes) {
    }
}
