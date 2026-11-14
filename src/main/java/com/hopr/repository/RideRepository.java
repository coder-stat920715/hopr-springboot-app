package com.hopr.repository;

import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByHostIdOrderByRideDateAscDepartureTimeAsc(Long hostId);

    List<Ride> findByHostIdAndRideDateOrderByDepartureTimeAsc(Long hostId, LocalDate date);

    List<Ride> findByRideDateAndStatusAndSeatsLeftGreaterThan(LocalDate date, RideStatus status, int seatsLeft);

    List<Ride> findByTemplateIdAndRideDateGreaterThanEqualAndStatus(Long templateId, LocalDate from, RideStatus status);

    List<Ride> findByHostIdAndRideDateGreaterThanEqualAndStatus(Long hostId, LocalDate from, RideStatus status);

    boolean existsByTemplateIdAndRideDate(Long templateId, LocalDate rideDate);
}
