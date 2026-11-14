package com.hopr.repository;

import com.hopr.entity.AccountAppeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountAppealRepository extends JpaRepository<AccountAppeal, Long> {
    Optional<AccountAppeal> findByAppealTicketNumber(String appealTicketNumber);

    List<AccountAppeal> findAllByOrderByCreatedAtDesc();
}
