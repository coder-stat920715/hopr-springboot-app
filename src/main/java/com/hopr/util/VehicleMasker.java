package com.hopr.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks vehicle registration numbers, e.g. TS07AB4821 -> TS-07-****-4821. */
public final class VehicleMasker {

    private static final Pattern REG = Pattern.compile("^([A-Z]{2})(\\d{1,2})([A-Z]{0,3})(\\d{4})$");

    private VehicleMasker() {
    }

    public static String normalize(String registration) {
        if (registration == null) {
            return null;
        }
        return registration.replaceAll("[\\s-]", "").toUpperCase();
    }

    public static String mask(String registration) {
        String reg = normalize(registration);
        if (reg == null || reg.isEmpty()) {
            return "";
        }
        Matcher m = REG.matcher(reg);
        if (m.matches()) {
            return m.group(1) + "-" + String.format("%02d", Integer.parseInt(m.group(2))) + "-****-" + m.group(4);
        }
        if (reg.length() <= 4) {
            return "****";
        }
        return "****" + reg.substring(reg.length() - 4);
    }
}
