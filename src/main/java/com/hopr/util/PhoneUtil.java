package com.hopr.util;

import com.hopr.exception.BadRequestException;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    /** Normalises Indian mobile numbers to +91XXXXXXXXXX. */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new BadRequestException("Phone number is required");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "+91" + digits.substring(1);
        }
        throw new BadRequestException("Invalid phone number");
    }
}
