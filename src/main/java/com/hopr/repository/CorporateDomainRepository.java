package com.hopr.repository;

import com.hopr.entity.CorporateDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorporateDomainRepository extends JpaRepository<CorporateDomain, Long> {
    Optional<CorporateDomain> findByDomainIgnoreCaseAndActiveTrue(String domain);

    boolean existsByDomainIgnoreCase(String domain);
}
