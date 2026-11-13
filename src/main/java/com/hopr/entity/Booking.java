package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ride_id")
    private Ride ride;

    @ManyToOne(optional = false)
    @JoinColumn(name = "guest_id")
    private User guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "pickup_label")),
            @AttributeOverride(name = "lat", column = @Column(name = "pickup_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "pickup_lng"))})
    private GeoPoint pickup;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "drop_label")),
            @AttributeOverride(name = "lat", column = @Column(name = "drop_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "drop_lng"))})
    private GeoPoint dropPoint;

    private int pickupWalkMeters;
    private double sharedRideKm;
    private int dropWalkMeters;
    private int matchPercentage;

    private LocalTime pickupWindowStart;
    private LocalTime pickupWindowEnd;

    @Column(precision = 10, scale = 2)
    private BigDecimal standardFare;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountedFare;

    /** true while the fare is held in escrow from the guest's wallet. */
    private boolean escrowHeld;

    private Instant createdAt;
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
