package com.hopr.repository;

import com.hopr.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByOwnerIdAndRemovedFalse(Long ownerId);

    Optional<Vehicle> findByIdAndOwnerIdAndRemovedFalse(Long id, Long ownerId);

    Optional<Vehicle> findFirstByOwnerIdAndActiveTrueAndRemovedFalse(Long ownerId);

    boolean existsByRegistrationNumber(String registrationNumber);

    long countByOwnerIdAndRemovedFalse(Long ownerId);
}
