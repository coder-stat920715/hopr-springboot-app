package com.hopr.service;

import com.hopr.dto.SupportDtos.TicketRequest;
import com.hopr.dto.SupportDtos.TicketResponse;
import com.hopr.dto.SupportDtos.TicketUpdateRequest;
import com.hopr.entity.SupportTicket;
import com.hopr.entity.User;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.SupportTicketRepository;
import com.hopr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository tickets;
    private final UserRepository users;
    private final NotificationService notifications;

    @Transactional
    public TicketResponse create(Long userId, TicketRequest req) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SupportTicket t = tickets.save(SupportTicket.builder()
                .user(u)
                .category(req.category())
                .subject(req.subject())
                .description(req.description())
                .rideId(req.rideId())
                .build());
        return toDto(t);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> mine(Long userId) {
        return tickets.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> all() {
        return tickets.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public TicketResponse respond(Long ticketId, TicketUpdateRequest req) {
        SupportTicket t = tickets.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (req.status() != null) {
            t.setStatus(req.status());
        }
        if (req.adminResponse() != null) {
            t.setAdminResponse(req.adminResponse());
        }
        tickets.save(t);
        notifications.notifyUser(t.getUser().getId(), "Your support ticket #" + t.getId() + " was updated");
        return toDto(t);
    }

    private TicketResponse toDto(SupportTicket t) {
        return new TicketResponse(t.getId(), t.getUser().getId(), t.getUser().getFullName(), t.getCategory(),
                t.getSubject(), t.getDescription(), t.getRideId(), t.getStatus(), t.getAdminResponse(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
