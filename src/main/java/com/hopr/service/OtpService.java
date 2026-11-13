package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.OtpPurpose;
import com.hopr.entity.OtpToken;
import com.hopr.exception.BadRequestException;
import com.hopr.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpTokenRepository otpTokens;
    private final HoprProperties props;

    @Transactional
    public String issue(String target, OtpPurpose purpose) {
        HoprProperties.Otp cfg = props.getOtp();
        String code;
        if (cfg.getFixedCode() != null && !cfg.getFixedCode().isBlank()) {
            code = cfg.getFixedCode();
        } else {
            int bound = (int) Math.pow(10, cfg.getLength());
            code = String.format("%0" + cfg.getLength() + "d", RANDOM.nextInt(bound));
        }
        otpTokens.save(OtpToken.builder()
                .target(target)
                .purpose(purpose)
                .code(code)
                .expiresAt(Instant.now().plusSeconds(cfg.getTtlMinutes() * 60L))
                .build());
        return code;
    }

    /**
     * Runs in its own transaction so that failed-attempt counters are persisted even when the
     * surrounding business transaction rolls back because of the thrown exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BadRequestException.class)
    public void verify(String target, OtpPurpose purpose, String code) {
        OtpToken token = otpTokens
                .findFirstByTargetAndPurposeAndUsedFalseOrderByCreatedAtDescIdDesc(target, purpose)
                .orElseThrow(() -> new BadRequestException("No OTP requested for this target. Please request a new OTP."));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setUsed(true);
            otpTokens.save(token);
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }
        if (token.getAttempts() >= props.getOtp().getMaxAttempts()) {
            token.setUsed(true);
            otpTokens.save(token);
            throw new BadRequestException("Too many incorrect attempts. Please request a new OTP.");
        }
        if (!token.getCode().equals(code)) {
            token.setAttempts(token.getAttempts() + 1);
            otpTokens.save(token);
            throw new BadRequestException("Incorrect OTP");
        }
        token.setUsed(true);
        otpTokens.save(token);
    }
}
