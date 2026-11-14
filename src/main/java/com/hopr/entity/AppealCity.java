package com.hopr.entity;

import com.hopr.exception.BadRequestException;

import java.util.Locale;

public enum AppealCity {
    HYDERABAD("Hyderabad"), KOLKATA("Kolkata"), BENGALURU("Bengaluru"), MUMBAI("Mumbai"),
    PUNE("Pune"), CHENNAI("Chennai"), DELHI_NCR("Delhi NCR");

    private final String label;

    AppealCity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts the enum name ("DELHI_NCR") or the display label ("Delhi NCR"). */
    public static AppealCity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("City is required");
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (AppealCity c : values()) {
            if (c.name().equals(key)) {
                return c;
            }
        }
        throw new BadRequestException("Unsupported city '" + raw + "'");
    }
}
