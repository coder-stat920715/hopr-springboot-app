package com.hopr.service;

import com.hopr.dto.TripDtos.RatingRequest;
import com.hopr.dto.TripDtos.RatingResponse;
import com.hopr.entity.Rating;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.RatingRepository;
import com.hopr.repository.RideRepository;
import com.hopr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratings;
    private final RideRepository rides;
    private final UserRepository users;
    private final TripAccessService access;

    @Transactional
    public RatingResponse rate(Long raterId, Long rideId, RatingRequest req) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ConflictException("You can rate only after the trip is COMPLETED");
        }
        Long rateeId = req.rateeId();
        if (raterId.equals(rateeId)) {
            throw new BadRequestException("You cannot rate yourself");
        }
        Set<Long> participants = access.participantIds(rideId);
        if (!participants.contains(raterId) || !participants.contains(rateeId)) {
            throw new ForbiddenException("Both users must have been part of this trip");
        }
        Long hostId = ride.getHost().getId();
        if (!raterId.equals(hostId) && !rateeId.equals(hostId)) {
            throw new BadRequestException("Ratings are exchanged between the host and a passenger");
        }
        if (ratings.existsByRideIdAndRaterIdAndRateeId(rideId, raterId, rateeId)) {
            throw new ConflictException("You already rated this user for this trip");
        }
        Rating saved = ratings.save(Rating.builder()
                .rideId(rideId)
                .raterId(raterId)
                .rateeId(rateeId)
                .stars(req.stars())
                .tags(req.tags() == null ? "" : String.join(",", req.tags()))
                .feedback(req.feedback())
                .build());

        User ratee = users.findById(rateeId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        double total = ratee.getRatingAverage() * ratee.getRatingCount() + req.stars();
        ratee.setRatingCount(ratee.getRatingCount() + 1);
        ratee.setRatingAverage(total / ratee.getRatingCount());
        users.save(ratee);

        List<String> tags = saved.getTags().isBlank() ? List.of() : Arrays.asList(saved.getTags().split(","));
        return new RatingResponse(saved.getId(), rideId, raterId, rateeId, saved.getStars(), tags, saved.getFeedback());
    }
}
