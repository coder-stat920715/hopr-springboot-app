package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    private String fullName;
    private String profilePhotoUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CommuteMode commuteMode = CommuteMode.GUEST;

    private String city;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "home_label")),
            @AttributeOverride(name = "lat", column = @Column(name = "home_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "home_lng"))})
    private GeoPoint home;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "office_label")),
            @AttributeOverride(name = "lat", column = @Column(name = "office_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "office_lng"))})
    private GeoPoint office;

    private String corporateEmail;
    private String pendingCorporateEmail;

    @Builder.Default
    private boolean corporateVerified = false;

    private String verifiedCompanyName;
    private String previousCompanyName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CompanyStatus companyStatus = CompanyStatus.NONE;

    private String licenceNumber;
    private LocalDate licenceExpiry;

    @Builder.Default
    private boolean licenceVerified = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name")
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Builder.Default
    private double ratingAverage = 0.0;

    @Builder.Default
    private int ratingCount = 0;

    @Column(unique = true)
    private String referralCode;

    private Long referredByUserId;

    @Builder.Default
    private boolean active = true;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
