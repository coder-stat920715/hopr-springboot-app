package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Binds a physical device (installation fingerprint) to the account that first used it. */
@Entity
@Table(name = "user_device_bindings", indexes = @Index(name = "idx_udb_device", columnList = "device_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;

    private String deviceModel;
    private String osPlatform;
    private Long boundUserId;
    private String boundPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeviceBindingStatus status = DeviceBindingStatus.ACTIVE;

    private Instant boundAt;
    private Instant unboundAt;

    @PrePersist
    void onCreate() {
        if (boundAt == null) {
            boundAt = Instant.now();
        }
    }
}
