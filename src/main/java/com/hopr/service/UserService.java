package com.hopr.service;

import com.hopr.dto.CommonDtos.MessageResponse;
import com.hopr.dto.UserDtos.CompanyResetRequest;
import com.hopr.dto.UserDtos.CorporateEmailRequest;
import com.hopr.dto.UserDtos.UpdateProfileRequest;
import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.dto.AuthDtos.SendOtpResponse;
import com.hopr.config.HoprProperties;
import com.hopr.entity.CommuteMode;
import com.hopr.entity.CompanyStatus;
import com.hopr.entity.CorporateDomain;
import com.hopr.entity.OtpPurpose;
import com.hopr.entity.SupportTicket;
import com.hopr.entity.TicketStatus;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.CorporateDomainRepository;
import com.hopr.repository.SupportTicketRepository;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository users;
    private final CorporateDomainRepository domains;
    private final VehicleRepository vehicles;
    private final SupportTicketRepository tickets;
    private final OtpService otpService;
    private final NotificationService notifications;
    private final DtoMapper mapper;
    private final HoprProperties props;

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public UserProfile me(Long userId) {
        return mapper.profile(getUser(userId));
    }

    // ------------------------------------------------------------------ profile

    @Transactional
    public UserProfile updateProfile(Long userId, UpdateProfileRequest req) {
        User u = getUser(userId);
        if (req.fullName() != null && !req.fullName().isBlank()) {
            u.setFullName(req.fullName().trim());
        }
        if (req.profilePhotoUrl() != null) {
            u.setProfilePhotoUrl(req.profilePhotoUrl());
        }
        if (req.city() != null && !req.city().isBlank()) {
            u.setCity(req.city().trim().toUpperCase(Locale.ROOT));
        }
        if (req.home() != null) {
            u.setHome(req.home().toGeoPoint());
        }
        if (req.office() != null) {
            u.setOffice(req.office().toGeoPoint());
        }
        if (req.commuteMode() != null) {
            if (req.commuteMode() == CommuteMode.HOST && vehicles.countByOwnerIdAndRemovedFalse(userId) == 0) {
                throw new BadRequestException("Register at least one vehicle before switching to HOST mode");
            }
            u.setCommuteMode(req.commuteMode());
        }
        return mapper.profile(users.save(u));
    }

    // ------------------------------------------------- first-time corporate email verification

    @Transactional
    public SendOtpResponse sendCorporateEmailOtp(Long userId, CorporateEmailRequest req) {
        User u = getUser(userId);
        if (u.isCorporateVerified() && u.getCompanyStatus() == CompanyStatus.VERIFIED) {
            throw new BadRequestException("Corporate email already verified for "
                    + u.getVerifiedCompanyName() + ". Use the company-change flow to switch employer.");
        }
        CorporateDomain domain = resolveDomain(req.corporateEmail());
        String email = req.corporateEmail().trim().toLowerCase(Locale.ROOT);
        u.setPendingCorporateEmail(email);
        users.save(u);
        return issueEmailOtp(email, domain, OtpPurpose.CORPORATE_EMAIL);
    }

    @Transactional
    public UserProfile verifyCorporateEmail(Long userId, String otp) {
        User u = getUser(userId);
        if (u.getPendingCorporateEmail() == null) {
            throw new BadRequestException("No corporate email verification in progress");
        }
        otpService.verify(u.getPendingCorporateEmail(), OtpPurpose.CORPORATE_EMAIL, otp);
        completeVerification(u);
        return mapper.profile(users.save(u));
    }

    // ------------------------------------------------------------ company change / re-verification

    @Transactional
    public SendOtpResponse initiateCompanyChange(Long userId, CorporateEmailRequest req) {
        User u = getUser(userId);
        CorporateDomain domain = resolveDomain(req.corporateEmail());
        String email = req.corporateEmail().trim().toLowerCase(Locale.ROOT);
        if (u.isCorporateVerified() && domain.getCompanyName().equalsIgnoreCase(u.getVerifiedCompanyName())) {
            throw new BadRequestException("You are already verified with " + u.getVerifiedCompanyName());
        }
        // Safely transition the badge: remember previous employer, drop the green badge until re-verified.
        if (u.getVerifiedCompanyName() != null) {
            u.setPreviousCompanyName(u.getVerifiedCompanyName());
        }
        u.setCompanyStatus(CompanyStatus.PENDING_REVERIFICATION);
        u.setCorporateVerified(false);
        u.setPendingCorporateEmail(email);
        users.save(u);
        return issueEmailOtp(email, domain, OtpPurpose.COMPANY_CHANGE);
    }

    @Transactional
    public UserProfile verifyCompanyChange(Long userId, String otp) {
        User u = getUser(userId);
        if (u.getCompanyStatus() != CompanyStatus.PENDING_REVERIFICATION || u.getPendingCorporateEmail() == null) {
            throw new BadRequestException("No company change in progress. Initiate a company change first.");
        }
        otpService.verify(u.getPendingCorporateEmail(), OtpPurpose.COMPANY_CHANGE, otp);
        completeVerification(u);
        return mapper.profile(users.save(u));
    }

    /** Admin / support override for users whose organisation field is locked. */
    @Transactional
    public UserProfile adminCompanyReset(Long adminId, CompanyResetRequest req) {
        User u = getUser(req.userId());
        if (u.getVerifiedCompanyName() != null) {
            u.setPreviousCompanyName(u.getVerifiedCompanyName());
        }
        u.setVerifiedCompanyName(null);
        u.setCorporateEmail(null);
        u.setPendingCorporateEmail(null);
        u.setCorporateVerified(false);
        u.setCompanyStatus(CompanyStatus.NONE);
        users.save(u);
        log.info("Admin {} reset company affiliation of user {}: {}", adminId, u.getId(), req.reason());

        if (req.ticketId() != null) {
            SupportTicket t = tickets.findById(req.ticketId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + req.ticketId()));
            t.setStatus(TicketStatus.RESOLVED);
            t.setAdminResponse("Company affiliation reset by support. Please verify your new corporate email."
                    + (req.reason() != null ? " Note: " + req.reason() : ""));
            tickets.save(t);
        }
        notifications.notifyUser(u.getId(), "Your company affiliation was reset by Hopr support. "
                + "Please verify your new corporate email to get the green badge.");
        return mapper.profile(u);
    }

    // -------------------------------------------------------------------------------- helpers

    private void completeVerification(User u) {
        CorporateDomain domain = resolveDomain(u.getPendingCorporateEmail());
        u.setCorporateEmail(u.getPendingCorporateEmail());
        u.setPendingCorporateEmail(null);
        u.setVerifiedCompanyName(domain.getCompanyName());
        u.setCorporateVerified(true);
        u.setCompanyStatus(CompanyStatus.VERIFIED);
    }

    private CorporateDomain resolveDomain(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            throw new BadRequestException("Invalid e-mail address");
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return domains.findByDomainIgnoreCaseAndActiveTrue(domain)
                .orElseThrow(() -> new BadRequestException("'" + domain + "' is not an allow-listed corporate domain"));
    }

    private SendOtpResponse issueEmailOtp(String email, CorporateDomain domain, OtpPurpose purpose) {
        String code = otpService.issue(email, purpose);
        notifications.sendEmail(email, "Hopr corporate verification",
                "Your Hopr verification code for " + domain.getCompanyName() + " is " + code);
        return new SendOtpResponse("Verification OTP sent to " + email, props.getOtp().getTtlMinutes() * 60L,
                props.getOtp().isExposeInResponse() ? code : null);
    }

    public MessageResponse message(String text) {
        return new MessageResponse(text);
    }
}
