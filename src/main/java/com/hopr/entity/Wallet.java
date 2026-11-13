package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    /** Amount currently held in escrow for confirmed bookings. */
    @Column(precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal escrow = BigDecimal.ZERO.setScale(2);
}
