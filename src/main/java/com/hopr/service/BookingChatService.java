package com.hopr.service;

import com.hopr.dto.BookingDtos.BookingChatMessageResponse;
import com.hopr.dto.TripDtos.MaskedCallResponse;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingMessage;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.MaskedCallSession;
import com.hopr.entity.Ride;
import com.hopr.entity.RideStatus;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.BookingMessageRepository;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.MaskedCallSessionRepository;
import com.hopr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pre-acceptance chat and masked call between host and guest, available from the Quick Action card. */
@Service
@RequiredArgsConstructor
public class BookingChatService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CALL_SESSION_MINUTES = 15;

    private final BookingRepository bookings;
    private final BookingMessageRepository messages;
    private final MaskedCallSessionRepository calls;
    private final UserRepository users;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public BookingChatMessageResponse send(Long bookingId, Long userId, String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Message cannot be empty");
        }
        Booking b = requireParticipant(bookingId, userId);
        requireOpen(b);
        User sender = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BookingMessage saved = messages.save(BookingMessage.builder()
                .bookingId(bookingId).senderId(userId).senderName(sender.getFullName()).content(content.trim()).build());
        BookingChatMessageResponse dto = toDto(saved);
        messaging.convertAndSend("/topic/bookings/" + bookingId, dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public List<BookingChatMessageResponse> history(Long bookingId, Long userId) {
        requireParticipant(bookingId, userId);
        return messages.findByBookingIdOrderBySentAtAscIdAsc(bookingId).stream().map(this::toDto).toList();
    }

    @Transactional
    public MaskedCallResponse maskedCall(Long bookingId, Long userId) {
        Booking b = requireParticipant(bookingId, userId);
        requireOpen(b);
        Ride ride = b.getRide();
        if (ride.getStatus() != RideStatus.SCHEDULED && ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ConflictException("Calls are only available for scheduled or ongoing rides");
        }
        Long calleeId = userId.equals(b.getGuest().getId()) ? ride.getHost().getId() : b.getGuest().getId();
        String virtualNumber = String.format("+9180%08d", RANDOM.nextInt(100_000_000));
        MaskedCallSession s = calls.save(MaskedCallSession.builder()
                .rideId(ride.getId()).bookingId(bookingId).callerId(userId).calleeId(calleeId)
                .virtualNumber(virtualNumber).sessionToken(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(CALL_SESSION_MINUTES * 60L)).build());
        return new MaskedCallResponse(s.getId(), s.getVirtualNumber(), s.getSessionToken(), s.getExpiresAt(),
                "Dial the virtual number from your registered phone within " + CALL_SESSION_MINUTES
                        + " minutes. Personal numbers are never shared.");
    }

    private Booking requireParticipant(Long bookingId, Long userId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        boolean guest = b.getGuest().getId().equals(userId);
        boolean host = b.getRide().getHost().getId().equals(userId);
        if (!guest && !host) {
            throw new ForbiddenException("Only the host and the requesting guest can access this booking");
        }
        return b;
    }

    private void requireOpen(Booking b) {
        if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("This request is " + b.getStatus() + "; chat and calls are closed");
        }
    }

    private BookingChatMessageResponse toDto(BookingMessage m) {
        return new BookingChatMessageResponse(m.getId(), m.getBookingId(), m.getSenderId(), m.getSenderName(),
                m.getContent(), m.getSentAt());
    }
}
