package com.hopr.util;

/** Geospatial helpers: Haversine distance and projection of a point onto a route segment. */
public final class GeoUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtil() {
    }

    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Result of projecting a point onto the straight route A -> B.
     *
     * @param t   fraction (0..1) along the route where the projection lies
     * @param lat latitude of the projected point on the route
     * @param lng longitude of the projected point on the route
     */
    public record Projection(double t, double lat, double lng) {
    }

    public static Projection projectOnSegment(double pLat, double pLng,
                                              double aLat, double aLng,
                                              double bLat, double bLng) {
        double kx = Math.cos(Math.toRadians(aLat)) * 111_320.0;
        double ky = 110_540.0;
        double bx = (bLng - aLng) * kx;
        double by = (bLat - aLat) * ky;
        double px = (pLng - aLng) * kx;
        double py = (pLat - aLat) * ky;
        double len2 = bx * bx + by * by;
        double t = len2 == 0 ? 0 : (px * bx + py * by) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        return new Projection(t, aLat + t * (bLat - aLat), aLng + t * (bLng - aLng));
    }

    public static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
