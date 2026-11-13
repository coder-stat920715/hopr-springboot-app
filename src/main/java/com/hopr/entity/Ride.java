package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "rides", indexes = @Index(name = "idx_ride_date_status", columnList = "ride_date,status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock: guarantees thread-safe seat decrement / no overbooking. */
    @Version
    private Long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    /** Master recurring template this ride was generated from (nullable for ad-hoc rides). */
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommuteDirection direction;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "start_label")),
            @AttributeOverride(name = "lat", column = @Column(name = "start_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "start_lng"))})
    private GeoPoint start;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "end_label")),
            @AttributeOverride(name = "lat", column = @Column(name = "end_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "end_lng"))})
    private GeoPoint end;

    @Column(nullable = false)
    private LocalDate rideDate;

    @Column(nullable = false)
    private LocalTime departureTime;

    private int totalSeats;
    private int seatsLeft;

    @Column(precision = 10, scale = 2)
    private BigDecimal ratePerKm;

    private double routeDistanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RideStatus status = RideStatus.SCHEDULED;

    private String city;
    private boolean luggageFriendly;

    /** true when this single occurrence was edited independently of its template. */
    private boolean customized;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
