package com.hopr.repository;

import com.hopr.entity.UserCorporateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCorporateHistoryRepository extends JpaRepository<UserCorporateHistory, Long> {
    List<UserCorporateHistory> findByUserIdOrderByArchivedAtDescIdDesc(Long userId);
}
