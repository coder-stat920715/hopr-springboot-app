package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Latest known live location of the host vehicle for a ride (one row per ride). */
@Entity
@Table(name = "location_updates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long rideId;

    private double lat;
    private double lng;
    private Double speedKmph;
    private int etaMinutes;
    private double remainingKm;
    private Instant updatedAt;
}
