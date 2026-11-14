package com.hopr.entity;

import com.hopr.exception.BadRequestException;

import java.util.Locale;

public enum AppealProfileType {
    HOST_RIDE_GIVER("Host - Ride Giver"), GUEST_RIDE_TAKER("Guest - Ride Taker");

    private final String label;

    AppealProfileType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts "HOST_RIDE_GIVER" or "Host - Ride Giver" (and the guest equivalents). */
    public static AppealProfileType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Profile type is required");
        }
        String trimmed = raw.trim();
        for (AppealProfileType t : values()) {
            if (t.name().equalsIgnoreCase(trimmed) || t.label.equalsIgnoreCase(trimmed)) {
                return t;
            }
        }
        throw new BadRequestException("Unsupported profile type '" + raw
                + "' (use 'Host - Ride Giver' or 'Guest - Ride Taker')");
    }
}
