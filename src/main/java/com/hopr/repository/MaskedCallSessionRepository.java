package com.hopr.repository;

import com.hopr.entity.MaskedCallSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaskedCallSessionRepository extends JpaRepository<MaskedCallSession, Long> {
}
