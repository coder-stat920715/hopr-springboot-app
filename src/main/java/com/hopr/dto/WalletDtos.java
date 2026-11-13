package com.hopr.dto;

import com.hopr.entity.TxnType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WalletDtos {
    private WalletDtos() {
    }

    public record TopupRequest(@NotNull @DecimalMin("1") @DecimalMax("50000") BigDecimal amount) {
    }

    public record TxnResponse(Long id, TxnType type, BigDecimal amount, BigDecimal balanceAfter,
                              String description, Long referenceId, Instant createdAt) {
    }

    public record WalletResponse(BigDecimal balance, BigDecimal escrowBalance, List<TxnResponse> transactions) {
    }

    public record ApplyReferralRequest(@NotBlank String code) {
    }

    public record ReferralSummary(String myCode, long successfulReferrals, BigDecimal totalEarned,
                                  BigDecimal lifetimeCap, BigDecimal remainingCap) {
    }
}
