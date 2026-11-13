package com.hopr.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TripDtos {
    private TripDtos() {
    }

    public record ChatMessageRequest(@NotBlank @Size(max = 1000) String content) {
    }

    public record ChatMessageResponse(Long id, Long rideId, Long senderId, String senderName,
                                      String content, Instant sentAt) {
    }

    public record MaskedCallRequest(@NotNull Long targetUserId) {
    }

    public record MaskedCallResponse(Long sessionId, String virtualNumber, String sessionToken,
                                     Instant expiresAt, String instructions) {
    }

    public record LocationUpdateRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            Double speedKmph) {
    }

    public record LocationResponse(Long rideId, double lat, double lng, Double speedKmph,
                                   int etaMinutes, double remainingKm, Instant updatedAt) {
    }

    public record RatingRequest(@NotNull Long rateeId, @Min(1) @Max(5) int stars,
                                List<String> tags, @Size(max = 1000) String feedback) {
    }

    public record RatingResponse(Long id, Long rideId, Long raterId, Long rateeId, int stars,
                                 List<String> tags, String feedback) {
    }
}
