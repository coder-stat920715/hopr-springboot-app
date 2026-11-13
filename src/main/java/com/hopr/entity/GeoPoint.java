package com.hopr.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {
    private String label;
    private Double lat;
    private Double lng;

    public static GeoPoint of(String label, double lat, double lng) {
        return new GeoPoint(label, lat, lng);
    }

    public GeoPoint copy() {
        return new GeoPoint(label, lat, lng);
    }
}
