package com.hopr.service;

import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.Ride;
import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/** Decides who belongs to a trip: the host plus every confirmed (or completed) guest. */
@Service
@RequiredArgsConstructor
public class TripAccessService {

    private final RideRepository rides;
    private final BookingRepository bookings;

    @Transactional(readOnly = true)
    public Set<Long> participantIds(Long rideId) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(ride.getHost().getId());
        for (Booking b : bookings.findByRideIdOrderByCreatedAtAsc(rideId)) {
            if (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED) {
                ids.add(b.getGuest().getId());
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(Long rideId, Long userId) {
        try {
            return participantIds(rideId).contains(userId);
        } catch (ResourceNotFoundException ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Ride requireParticipant(Long rideId, Long userId) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
        if (!participantIds(rideId).contains(userId)) {
            throw new ForbiddenException("Only the host and confirmed passengers can access this trip");
        }
        return ride;
    }
}
