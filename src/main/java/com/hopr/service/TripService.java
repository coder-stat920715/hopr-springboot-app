package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.dto.TripDtos.ChatMessageResponse;
import com.hopr.dto.TripDtos.LocationResponse;
import com.hopr.dto.TripDtos.LocationUpdateRequest;
import com.hopr.dto.TripDtos.MaskedCallResponse;
import com.hopr.entity.ChatMessage;
import com.hopr.entity.LocationUpdate;
import com.hopr.entity.MaskedCallSession;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.ChatMessageRepository;
import com.hopr.repository.LocationUpdateRepository;
import com.hopr.repository.MaskedCallSessionRepository;
import com.hopr.repository.UserRepository;
import com.hopr.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CALL_SESSION_MINUTES = 15;

    private final TripAccessService access;
    private final ChatMessageRepository chat;
    private final MaskedCallSessionRepository calls;
    private final LocationUpdateRepository locations;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;
    private final HoprProperties props;

    // ------------------------------------------------------------------------------- chat

    @Transactional
    public ChatMessageResponse sendChat(Long rideId, Long userId, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Message cannot be empty");
        }
        access.requireParticipant(rideId, userId);
        User sender = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ChatMessage saved = chat.save(ChatMessage.builder()
                .rideId(rideId)
                .senderId(userId)
                .senderName(sender.getFullName())
                .content(content.trim())
                .build());
        ChatMessageResponse dto = toDto(saved);
        messaging.convertAndSend("/topic/trips/" + rideId, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(Long rideId, Long userId) {
        access.requireParticipant(rideId, userId);
        return chat.findByRideIdOrderBySentAtAscIdAsc(rideId).stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------------- masked call

    @Transactional
    public MaskedCallResponse maskedCall(Long rideId, Long callerId, Long targetUserId) {
        Ride ride = access.requireParticipant(rideId, callerId);
        if (callerId.equals(targetUserId)) {
            throw new BadRequestException("You cannot call yourself");
        }
        if (!access.participantIds(rideId).contains(targetUserId)) {
            throw new BadRequestException("The other person is not part of this trip");
        }
        Long hostId = ride.getHost().getId();
        if (!callerId.equals(hostId) && !targetUserId.equals(hostId)) {
            throw new ForbiddenException("Masked calls connect the host and a passenger only");
        }
        if (ride.getStatus() != RideStatus.SCHEDULED && ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ConflictException("Calls are only available for scheduled or ongoing trips");
        }
        String virtualNumber = String.format("+9180%08d", RANDOM.nextInt(100_000_000));
        MaskedCallSession s = calls.save(MaskedCallSession.builder()
                .rideId(rideId)
                .callerId(callerId)
                .calleeId(targetUserId)
                .virtualNumber(virtualNumber)
                .sessionToken(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(CALL_SESSION_MINUTES * 60L))
                .build());
        return new MaskedCallResponse(s.getId(), s.getVirtualNumber(), s.getSessionToken(), s.getExpiresAt(),
                "Dial the virtual number from your registered phone within " + CALL_SESSION_MINUTES
                        + " minutes. Personal numbers are never shared.");
    }

    // --------------------------------------------------------------------- live location

    @Transactional
    public LocationResponse updateLocation(Long rideId, Long userId, LocationUpdateRequest req) {
        Ride ride = access.requireParticipant(rideId, userId);
        if (!ride.getHost().getId().equals(userId)) {
            throw new ForbiddenException("Only the host shares the vehicle location");
        }
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ConflictException("Live location is only available while the ride is IN_PROGRESS");
        }
        double remainingKm = GeoUtil.round2(GeoUtil.haversineMeters(req.lat(), req.lng(),
                ride.getEnd().getLat(), ride.getEnd().getLng()) * props.getMatching().getRoadFactor() / 1000.0);
        double speed = req.speedKmph() != null && req.speedKmph() >= 5 ? req.speedKmph()
                : props.getMatching().getAverageSpeedKmph();
        int eta = (int) Math.ceil(remainingKm / speed * 60.0);

        LocationUpdate lu = locations.findByRideId(rideId)
                .orElseGet(() -> LocationUpdate.builder().rideId(rideId).build());
        lu.setLat(req.lat());
        lu.setLng(req.lng());
        lu.setSpeedKmph(req.speedKmph());
        lu.setRemainingKm(remainingKm);
        lu.setEtaMinutes(eta);
        lu.setUpdatedAt(Instant.now());
        locations.save(lu);

        LocationResponse dto = toDto(lu);
        messaging.convertAndSend("/topic/trips/" + rideId + "/location", dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public LocationResponse getLocation(Long rideId, Long userId) {
        access.requireParticipant(rideId, userId);
        return locations.findByRideId(rideId).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("No live location shared yet for this ride"));
    }

    private ChatMessageResponse toDto(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getRideId(), m.getSenderId(), m.getSenderName(),
                m.getContent(), m.getSentAt());
    }

    private LocationResponse toDto(LocationUpdate l) {
        return new LocationResponse(l.getRideId(), l.getLat(), l.getLng(), l.getSpeedKmph(), l.getEtaMinutes(),
                l.getRemainingKm(), l.getUpdatedAt());
    }
}
