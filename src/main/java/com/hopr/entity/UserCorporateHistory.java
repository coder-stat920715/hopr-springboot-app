package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Audit trail of previous employer affiliations, written when an employer switch completes. */
@Entity
@Table(name = "user_corporate_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCorporateHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private String companyName;
    private String corporateEmail;
    private String city;
    private Instant verifiedFrom;
    private Instant archivedAt;

    @PrePersist
    void onCreate() {
        if (archivedAt == null) {
            archivedAt = Instant.now();
        }
    }
}
