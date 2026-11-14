package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Mirrors the fields of Hopr's "Account Login Appeal Form" (https://survey.zohopublic.in/zs/ekBH9S). */
@Entity
@Table(name = "account_appeals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountAppeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String appealTicketNumber;

    private String registeredName;

    @Column(nullable = false)
    private String registeredPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppealCity registeredCity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppealProfileType profileType;

    private String previousRegisteredPhoneNumber;

    /** The blocked device, when the appeal was raised from the "Device account limit reached" screen. */
    private String deviceId;

    @Column(nullable = false, length = 20000)
    private String issueDescription;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "appeal_attachments", joinColumns = @JoinColumn(name = "appeal_id"))
    @Column(name = "file_path")
    @Builder.Default
    private List<String> screenshotAttachments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AppealStatus status = AppealStatus.SUBMITTED;

    @Column(length = 2000)
    private String adminRemarks;

    @Column(length = 2000)
    private String resolutionActions;

    private Long resolvedByAdminId;
    private Instant createdAt;
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
