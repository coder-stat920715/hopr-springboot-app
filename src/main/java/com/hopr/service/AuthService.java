package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.AuthDtos.RefreshRequest;
import com.hopr.dto.AuthDtos.SendOtpRequest;
import com.hopr.dto.AuthDtos.SendOtpResponse;
import com.hopr.dto.AuthDtos.TokenResponse;
import com.hopr.dto.AuthDtos.VerifyOtpRequest;
import com.hopr.entity.OtpPurpose;
import com.hopr.entity.User;
import com.hopr.exception.UnauthorizedException;
import com.hopr.repository.UserRepository;
import com.hopr.security.JwtService;
import com.hopr.util.PhoneUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final OtpService otpService;
    private final NotificationService notifications;
    private final WalletService walletService;
    private final JwtService jwtService;
    private final DtoMapper mapper;
    private final HoprProperties props;

    public SendOtpResponse sendOtp(SendOtpRequest req) {
        String phone = PhoneUtil.normalize(req.phone());
        String code = otpService.issue(phone, OtpPurpose.LOGIN);
        notifications.sendSms(phone, "Your Hopr verification code is " + code + ". Valid for "
                + props.getOtp().getTtlMinutes() + " minutes.");
        return new SendOtpResponse("OTP sent to " + phone, props.getOtp().getTtlMinutes() * 60L,
                props.getOtp().isExposeInResponse() ? code : null);
    }

    @Transactional
    public TokenResponse verifyOtp(VerifyOtpRequest req) {
        String phone = PhoneUtil.normalize(req.phone());
        otpService.verify(phone, OtpPurpose.LOGIN, req.otp());

        boolean isNew = false;
        User user = users.findByPhone(phone).orElse(null);
        if (user == null) {
            Set<String> roles = new HashSet<>();
            roles.add("ROLE_USER");
            user = users.save(User.builder()
                    .phone(phone)
                    .fullName(req.fullName() != null && !req.fullName().isBlank() ? req.fullName().trim() : "Hopr User")
                    .roles(roles)
                    .referralCode(generateReferralCode())
                    .build());
            walletService.getOrCreate(user.getId());
            isNew = true;
        } else if (req.fullName() != null && !req.fullName().isBlank()
                && (user.getFullName() == null || user.getFullName().equals("Hopr User"))) {
            user.setFullName(req.fullName().trim());
            user = users.save(user);
        }
        return tokens(user, isNew);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest req) {
        Claims claims = jwtService.parse(req.refreshToken());
        if (!jwtService.isRefreshToken(claims)) {
            throw new UnauthorizedException("Not a refresh token");
        }
        User user = users.findById(jwtService.userId(claims))
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }
        return tokens(user, false);
    }

    public String generateReferralCode() {
        while (true) {
            StringBuilder sb = new StringBuilder("HOPR");
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!users.existsByReferralCode(code)) {
                return code;
            }
        }
    }

    private TokenResponse tokens(User user, boolean isNew) {
        return new TokenResponse(jwtService.createAccessToken(user), jwtService.createRefreshToken(user),
                "Bearer", jwtService.accessTtlSeconds(), isNew, mapper.profile(user));
    }
}
