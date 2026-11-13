package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "referrals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Referral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "referrer_id")
    private User referrer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "referee_id", unique = true)
    private User referee;

    @Column(precision = 12, scale = 2)
    private BigDecimal referrerReward;

    @Column(precision = 12, scale = 2)
    private BigDecimal refereeReward;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
