package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "corporate_domains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorporateDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(nullable = false)
    private String companyName;

    @Builder.Default
    private boolean active = true;
}
