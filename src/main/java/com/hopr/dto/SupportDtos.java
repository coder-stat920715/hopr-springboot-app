package com.hopr.dto;

import com.hopr.entity.TicketCategory;
import com.hopr.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class SupportDtos {
    private SupportDtos() {
    }

    public record TicketRequest(@NotNull TicketCategory category, @NotBlank String subject,
                                @NotBlank String description, Long rideId) {
    }

    public record TicketUpdateRequest(TicketStatus status, String adminResponse) {
    }

    public record TicketResponse(Long id, Long userId, String userName, TicketCategory category,
                                 String subject, String description, Long rideId, TicketStatus status,
                                 String adminResponse, Instant createdAt, Instant updatedAt) {
    }
}
