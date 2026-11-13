package com.hopr.repository;

import com.hopr.entity.LocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationUpdateRepository extends JpaRepository<LocationUpdate, Long> {
    Optional<LocationUpdate> findByRideId(Long rideId);
}
