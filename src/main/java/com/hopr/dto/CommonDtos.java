package com.hopr.dto;

import com.hopr.entity.GeoPoint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public final class CommonDtos {
    private CommonDtos() {
    }

    public record LocationDto(
            String label,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {

        public static LocationDto from(GeoPoint p) {
            if (p == null || p.getLat() == null || p.getLng() == null) {
                return null;
            }
            return new LocationDto(p.getLabel(), p.getLat(), p.getLng());
        }

        public GeoPoint toGeoPoint() {
            return new GeoPoint(label, lat, lng);
        }
    }

    public record CorporateBadge(boolean verified, String companyName, String status, String badgeColor) {
    }

    public record PublicUser(Long id, String name, String photoUrl, CorporateBadge corporateBadge,
                             double rating, int ratingCount) {
    }

    public record MessageResponse(String message) {
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String message,
                                Map<String, String> fieldErrors) {
    }
}
