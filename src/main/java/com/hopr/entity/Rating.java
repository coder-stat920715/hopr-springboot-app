package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ratings", uniqueConstraints = @UniqueConstraint(columnNames = {"ride_id", "rater_id", "ratee_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long rideId;
    private Long raterId;
    private Long rateeId;
    private int stars;

    /** comma separated feedback tags, e.g. "Punctual,Friendly". */
    private String tags;

    @Column(length = 1000)
    private String feedback;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
