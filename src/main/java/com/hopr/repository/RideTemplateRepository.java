package com.hopr.repository;

import com.hopr.entity.RideTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideTemplateRepository extends JpaRepository<RideTemplate, Long> {
    List<RideTemplate> findByHostIdAndActiveTrue(Long hostId);

    List<RideTemplate> findByHostIdOrderByIdAsc(Long hostId);

    List<RideTemplate> findByActiveTrue();
}
