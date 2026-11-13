package com.hopr.service;

import com.hopr.dto.WalletDtos.TxnResponse;
import com.hopr.dto.WalletDtos.WalletResponse;
import com.hopr.entity.TxnType;
import com.hopr.entity.Wallet;
import com.hopr.entity.WalletTransaction;
import com.hopr.exception.BadRequestException;
import com.hopr.repository.WalletRepository;
import com.hopr.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository txns;

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Wallet getOrCreate(Long userId) {
        return wallets.findByUserId(userId).orElseGet(() -> wallets.save(Wallet.builder().userId(userId).build()));
    }

    @Transactional
    public WalletResponse view(Long userId) {
        Wallet w = getOrCreate(userId);
        List<TxnResponse> history = txns.findTop50ByUserIdOrderByIdDesc(userId).stream()
                .map(t -> new TxnResponse(t.getId(), t.getTxnType(), t.getAmount(), t.getBalanceAfter(),
                        t.getDescription(), t.getReferenceId(), t.getCreatedAt()))
                .toList();
        return new WalletResponse(w.getBalance(), w.getEscrow(), history);
    }

    @Transactional
    public WalletResponse topup(Long userId, BigDecimal amount) {
        credit(userId, amount, TxnType.TOPUP, "Wallet top-up", null);
        return view(userId);
    }

    /** Adds money to the available balance (top-up, payouts, referral bonuses, compensation). */
    @Transactional
    public void credit(Long userId, BigDecimal amount, TxnType type, String description, Long referenceId) {
        BigDecimal amt = money(amount);
        if (amt.signum() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
        Wallet w = getOrCreate(userId);
        w.setBalance(money(w.getBalance().add(amt)));
        wallets.save(w);
        record(userId, type, amt, w.getBalance(), description, referenceId);
    }

    /** Moves the fare from the guest's available balance into escrow. */
    @Transactional
    public void holdEscrow(Long guestId, BigDecimal fare, Long bookingId) {
        BigDecimal amt = money(fare);
        Wallet w = getOrCreate(guestId);
        if (w.getBalance().compareTo(amt) < 0) {
            throw new BadRequestException("Insufficient Hopr wallet balance. Required: " + amt
                    + ", available: " + w.getBalance());
        }
        w.setBalance(money(w.getBalance().subtract(amt)));
        w.setEscrow(money(w.getEscrow().add(amt)));
        wallets.save(w);
        record(guestId, TxnType.ESCROW_HOLD, amt, w.getBalance(), "Fare held in escrow for booking #" + bookingId, bookingId);
    }

    /** Returns an escrowed fare to the guest's available balance. */
    @Transactional
    public void refundEscrow(Long guestId, BigDecimal fare, Long bookingId, String reason) {
        BigDecimal amt = money(fare);
        Wallet w = getOrCreate(guestId);
        w.setEscrow(money(w.getEscrow().subtract(amt)));
        w.setBalance(money(w.getBalance().add(amt)));
        wallets.save(w);
        record(guestId, TxnType.ESCROW_REFUND, amt, w.getBalance(), reason, bookingId);
    }

    /** Trip completed: releases the guest's escrow and credits the host's payout. */
    @Transactional
    public void settleEscrow(Long guestId, Long hostId, BigDecimal fare, BigDecimal hostPayout, Long bookingId) {
        Wallet guest = getOrCreate(guestId);
        guest.setEscrow(money(guest.getEscrow().subtract(money(fare))));
        wallets.save(guest);
        credit(hostId, hostPayout, TxnType.PAYOUT, "Fuel-share payout for booking #" + bookingId, bookingId);
    }

    /** Late cancellation: releases escrow, refunds fare minus penalty to guest, pays the penalty to the host. */
    @Transactional
    public void settleWithPenalty(Long guestId, Long hostId, BigDecimal fare, BigDecimal penalty, Long bookingId) {
        BigDecimal fareAmt = money(fare);
        BigDecimal pen = money(penalty);
        Wallet guest = getOrCreate(guestId);
        guest.setEscrow(money(guest.getEscrow().subtract(fareAmt)));
        guest.setBalance(money(guest.getBalance().add(fareAmt.subtract(pen))));
        wallets.save(guest);
        record(guestId, TxnType.ESCROW_REFUND, fareAmt.subtract(pen), guest.getBalance(),
                "Refund after late-cancellation penalty for booking #" + bookingId, bookingId);
        record(guestId, TxnType.CANCELLATION_PENALTY, pen, guest.getBalance(),
                "Late cancellation penalty for booking #" + bookingId, bookingId);
        if (pen.signum() > 0) {
            credit(hostId, pen, TxnType.CANCELLATION_COMPENSATION,
                    "Compensation for late cancellation of booking #" + bookingId, bookingId);
        }
    }

    private void record(Long userId, TxnType type, BigDecimal amount, BigDecimal balanceAfter,
                        String description, Long referenceId) {
        txns.save(WalletTransaction.builder().userId(userId).txnType(type).amount(amount)
                .balanceAfter(balanceAfter).description(description).referenceId(referenceId).build());
    }
}
