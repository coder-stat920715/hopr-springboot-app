package com.hopr.repository;

import com.hopr.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    boolean existsByRefereeId(Long refereeId);

    List<Referral> findByReferrerId(Long referrerId);
}
