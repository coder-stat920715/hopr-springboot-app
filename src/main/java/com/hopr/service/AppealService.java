package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.AppealDtos.AppealConfigResponse;
import com.hopr.dto.AppealDtos.AppealDecision;
import com.hopr.dto.AppealDtos.AppealRequest;
import com.hopr.dto.AppealDtos.AppealResponse;
import com.hopr.dto.AppealDtos.AppealStatusResponse;
import com.hopr.dto.AppealDtos.Option;
import com.hopr.dto.AppealDtos.ResolveAppealRequest;
import com.hopr.entity.AccountAppeal;
import com.hopr.entity.AppealCity;
import com.hopr.entity.AppealProfileType;
import com.hopr.entity.AppealStatus;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.AccountAppealRepository;
import com.hopr.repository.UserRepository;
import com.hopr.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Account login / profile appeals (mirrors the Zoho appeal form) and the admin resolution workflow. */
@Service
@RequiredArgsConstructor
public class AppealService {

    public static final int MIN_ISSUE = 10;
    public static final int MAX_ISSUE = 20000;

    private final AccountAppealRepository appeals;
    private final UserRepository users;
    private final DeviceBindingService deviceBindings;
    private final RideService rideService;
    private final AppealStorageService storage;
    private final NotificationService notifications;
    private final HoprProperties props;
    private final Clock clock;

    @Transactional
    public AppealResponse submit(AppealRequest req, List<MultipartFile> screenshots) {
        AppealCity city = AppealCity.parse(req.registeredCity());
        AppealProfileType type = AppealProfileType.parse(req.profileType());
        String issue = req.issueDescription() == null ? "" : req.issueDescription().trim();
        if (issue.length() < MIN_ISSUE || issue.length() > MAX_ISSUE) {
            throw new BadRequestException("Explain the issue in " + MIN_ISSUE + " to " + MAX_ISSUE + " characters");
        }
        PhoneUtil.normalize(req.registeredPhoneNumber()); // validates the number
        String previous = req.previousRegisteredPhoneNumber() == null || req.previousRegisteredPhoneNumber().isBlank()
                ? null : req.previousRegisteredPhoneNumber().trim();
        if (previous != null) {
            PhoneUtil.normalize(previous);
        }
        AccountAppeal a = appeals.save(AccountAppeal.builder()
                .registeredName(req.registeredName() == null ? null : req.registeredName().trim())
                .registeredPhoneNumber(req.registeredPhoneNumber().trim())
                .registeredCity(city)
                .profileType(type)
                .previousRegisteredPhoneNumber(previous)
                .deviceId(req.deviceId() == null || req.deviceId().isBlank() ? null : req.deviceId().trim())
                .issueDescription(issue)
                .status(AppealStatus.SUBMITTED)
                .build());
        String ticket = String.format("HPR-APL-%d-%04d", LocalDate.now(clock).getYear(), a.getId());
        a.setAppealTicketNumber(ticket);
        a.setScreenshotAttachments(storage.store(ticket, screenshots));
        a = appeals.save(a);
        notifications.sendSms(PhoneUtil.normalize(a.getRegisteredPhoneNumber()),
                "Hopr: your appeal " + ticket + " was received. We will update you shortly.");
        return toDto(a);
    }

    @Transactional(readOnly = true)
    public AppealStatusResponse status(String ticket) {
        AccountAppeal a = appeals.findByAppealTicketNumber(ticket.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("No appeal found for ticket " + ticket));
        String msg = switch (a.getStatus()) {
            case SUBMITTED -> "We have received your appeal and it is waiting for review.";
            case UNDER_REVIEW -> "Our support team is reviewing your appeal.";
            case APPROVED -> "Your appeal was approved. You can now log in or update your profile.";
            case REJECTED -> "Your appeal was not approved. See the admin remarks for details.";
        };
        return new AppealStatusResponse(a.getAppealTicketNumber(), a.getStatus(), msg, a.getAdminRemarks(),
                a.getCreatedAt(), a.getResolvedAt());
    }

    public AppealConfigResponse config() {
        List<Option> cities = Arrays.stream(AppealCity.values()).map(c -> new Option(c.name(), c.getLabel())).toList();
        List<Option> types = Arrays.stream(AppealProfileType.values()).map(t -> new Option(t.name(), t.getLabel())).toList();
        return new AppealConfigResponse("Account Login Appeal Form", props.getAppeals().getExternalFormUrl(),
                "/api/v1/appeals/submit", "/api/v1/appeals/status/{appealTicketNumber}", MIN_ISSUE, MAX_ISSUE,
                props.getAppeals().getMaxAttachments(), props.getAppeals().getMaxFileSizeBytes() / (1024 * 1024),
                cities, types);
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> list() {
        return appeals.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AppealResponse get(Long id) {
        return toDto(find(id));
    }

    /**
     * Admin decision. On approval the selected remediation flags are applied atomically:
     * device unlock, company / corporate e-mail unlock and a city change (archiving the old commutes).
     */
    @Transactional
    public AppealResponse resolve(Long adminId, Long appealId, ResolveAppealRequest req) {
        AccountAppeal a = find(appealId);
        if (a.getStatus() == AppealStatus.APPROVED || a.getStatus() == AppealStatus.REJECTED) {
            throw new ConflictException("Appeal " + a.getAppealTicketNumber() + " is already " + a.getStatus());
        }
        AppealDecision decision = req.decision() == null ? AppealDecision.APPROVED : req.decision();
        a.setAdminRemarks(req.adminRemarks());
        a.setResolvedByAdminId(adminId);

        if (decision == AppealDecision.UNDER_REVIEW) {
            a.setStatus(AppealStatus.UNDER_REVIEW);
            return toDto(appeals.save(a));
        }
        if (decision == AppealDecision.REJECTED) {
            a.setStatus(AppealStatus.REJECTED);
            a.setResolvedAt(Instant.now());
            appeals.save(a);
            notifications.sendSms(PhoneUtil.normalize(a.getRegisteredPhoneNumber()),
                    "Hopr: your appeal " + a.getAppealTicketNumber() + " was not approved.");
            return toDto(a);
        }

        List<String> actions = new ArrayList<>();
        if (req.unlockDeviceLimit()) {
            String previous = a.getPreviousRegisteredPhoneNumber() == null ? null
                    : PhoneUtil.normalize(a.getPreviousRegisteredPhoneNumber());
            int released = deviceBindings.unbindByAppeal(a.getDeviceId(), previous);
            actions.add("Device limit unlocked (" + released + " binding(s) released)");
        }
        boolean needsAccount = req.unlockCompanyAndEmail() || (req.targetCity() != null && !req.targetCity().isBlank());
        if (needsAccount) {
            User account = resolveAccount(a);
            if (req.unlockCompanyAndEmail()) {
                account.setCompanyLocked(false);
                account.setCorporateEmailLocked(false);
                account.setCorporateLockReason(null);
                actions.add("Company and corporate e-mail unlocked");
            }
            if (req.targetCity() != null && !req.targetCity().isBlank()) {
                AppealCity city = AppealCity.parse(req.targetCity());
                account.setCity(city.name());
                RideService.ArchiveResult r = rideService.archiveAllActiveCommutes(account.getId(),
                        "City updated by appeal " + a.getAppealTicketNumber());
                actions.add("City set to " + city.name() + " (" + r.templates() + " commute template(s) archived, "
                        + r.cancelledRides() + " upcoming ride(s) cancelled)");
            }
            users.save(account);
        }
        a.setStatus(AppealStatus.APPROVED);
        a.setResolvedAt(Instant.now());
        a.setResolutionActions(String.join("; ", actions));
        appeals.save(a);
        notifications.sendSms(PhoneUtil.normalize(a.getRegisteredPhoneNumber()),
                "Hopr: your appeal " + a.getAppealTicketNumber() + " was approved.");
        return toDto(a);
    }

    /** The account to unlock: the previously registered number if it exists, otherwise the registered number. */
    private User resolveAccount(AccountAppeal a) {
        if (a.getPreviousRegisteredPhoneNumber() != null) {
            var prev = users.findByPhone(PhoneUtil.normalize(a.getPreviousRegisteredPhoneNumber()));
            if (prev.isPresent()) {
                return prev.get();
            }
        }
        return users.findByPhone(PhoneUtil.normalize(a.getRegisteredPhoneNumber()))
                .orElseThrow(() -> new BadRequestException("No Hopr account found for the appeal's phone number"));
    }

    private AccountAppeal find(Long id) {
        return appeals.findById(id).orElseThrow(() -> new ResourceNotFoundException("Appeal not found: " + id));
    }

    private AppealResponse toDto(AccountAppeal a) {
        return new AppealResponse(a.getId(), a.getAppealTicketNumber(), a.getRegisteredName(),
                a.getRegisteredPhoneNumber(), a.getRegisteredCity().name(), a.getProfileType().name(),
                a.getProfileType().getLabel(), a.getPreviousRegisteredPhoneNumber(), a.getDeviceId(),
                a.getIssueDescription(), new ArrayList<>(a.getScreenshotAttachments()), a.getStatus(),
                a.getAdminRemarks(), a.getResolutionActions(), a.getCreatedAt(), a.getResolvedAt());
    }
}
