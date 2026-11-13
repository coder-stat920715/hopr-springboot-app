package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.WalletDtos.ReferralSummary;
import com.hopr.entity.Referral;
import com.hopr.entity.TxnType;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.ReferralRepository;
import com.hopr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referrals;
    private final UserRepository users;
    private final WalletService walletService;
    private final HoprProperties props;

    @Transactional
    public ReferralSummary apply(Long userId, String code) {
        User referee = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User referrer = users.findByReferralCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Invalid referral code"));
        if (referrer.getId().equals(referee.getId())) {
            throw new BadRequestException("You cannot use your own referral code");
        }
        if (referrals.existsByRefereeId(referee.getId())) {
            throw new BadRequestException("A referral code has already been applied to this account");
        }

        BigDecimal earned = totalEarned(referrer.getId());
        BigDecimal remaining = props.getReferral().getLifetimeCap().subtract(earned).max(BigDecimal.ZERO);
        BigDecimal referrerReward = props.getReferral().getReferrerReward().min(remaining);
        BigDecimal refereeReward = props.getReferral().getRefereeReward();

        Referral r = referrals.save(Referral.builder()
                .referrer(referrer)
                .referee(referee)
                .referrerReward(referrerReward)
                .refereeReward(refereeReward)
                .build());
        referee.setReferredByUserId(referrer.getId());
        users.save(referee);

        if (referrerReward.signum() > 0) {
            walletService.credit(referrer.getId(), referrerReward, TxnType.REFERRAL_BONUS,
                    "Referral bonus: " + referee.getFullName() + " joined using your code", r.getId());
        }
        if (refereeReward.signum() > 0) {
            walletService.credit(referee.getId(), refereeReward, TxnType.REFERRAL_BONUS,
                    "Welcome bonus for using referral code " + referrer.getReferralCode(), r.getId());
        }
        return summary(userId);
    }

    @Transactional(readOnly = true)
    public ReferralSummary summary(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Referral> mine = referrals.findByReferrerId(userId);
        BigDecimal earned = totalEarned(userId);
        BigDecimal cap = props.getReferral().getLifetimeCap();
        return new ReferralSummary(u.getReferralCode(), mine.size(), earned, cap,
                cap.subtract(earned).max(BigDecimal.ZERO));
    }

    private BigDecimal totalEarned(Long referrerId) {
        return referrals.findByReferrerId(referrerId).stream()
                .map(Referral::getReferrerReward)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
