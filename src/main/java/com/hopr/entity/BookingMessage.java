package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Pre-acceptance chat between host and guest, scoped to one booking request. */
@Entity
@Table(name = "booking_messages", indexes = @Index(name = "idx_bmsg_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private Long senderId;

    private String senderName;

    @Column(nullable = false, length = 1000)
    private String content;

    private Instant sentAt;

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }
}
