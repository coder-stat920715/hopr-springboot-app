package com.hopr.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType vehicleType;

    private String make;
    private String model;
    private String color;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    private int seats;

    /** The owner's currently selected (default) vehicle. */
    private boolean active;

    private boolean luggageFriendly;

    private boolean removed;
}
