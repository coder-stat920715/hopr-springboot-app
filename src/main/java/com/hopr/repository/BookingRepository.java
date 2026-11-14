package com.hopr.repository;

import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByRideIdOrderByCreatedAtAsc(Long rideId);

    List<Booking> findByRideIdAndStatus(Long rideId, BookingStatus status);

    List<Booking> findByGuestIdOrderByCreatedAtDesc(Long guestId);

    boolean existsByRideIdAndGuestIdAndStatusIn(Long rideId, Long guestId, Collection<BookingStatus> statuses);

    List<Booking> findByRideHostIdAndStatusOrderByCreatedAtAsc(Long hostId, BookingStatus status);

    long countByRideIdAndStatus(Long rideId, BookingStatus status);
}
