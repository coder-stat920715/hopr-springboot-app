package com.hopr.repository;

import com.hopr.entity.OtpPurpose;
import com.hopr.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findFirstByTargetAndPurposeAndUsedFalseOrderByCreatedAtDescIdDesc(String target, OtpPurpose purpose);
}
