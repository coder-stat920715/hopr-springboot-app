package com.hopr.repository;

import com.hopr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);

    Optional<User> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);
}
