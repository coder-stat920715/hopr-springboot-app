package com.hopr.repository;

import com.hopr.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByRideIdAndRaterIdAndRateeId(Long rideId, Long raterId, Long rateeId);
}
