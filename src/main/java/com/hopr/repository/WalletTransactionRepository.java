package com.hopr.repository;

import com.hopr.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserIdAndTxnType(Long userId, com.hopr.entity.TxnType txnType);

    List<WalletTransaction> findTop50ByUserIdOrderByIdDesc(Long userId);
}
