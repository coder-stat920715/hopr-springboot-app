package com.hopr.dto;

import com.hopr.entity.VehicleType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class VehicleDtos {
    private VehicleDtos() {
    }

    public record VehicleRequest(@NotNull VehicleType type, @NotBlank String make, @NotBlank String model,
                                 String color, @NotBlank String registrationNumber,
                                 @Min(1) @Max(4) int seats, boolean luggageFriendly) {
    }

    /** Owner view: contains the full registration number. */
    public record VehicleResponse(Long id, VehicleType type, String make, String model, String color,
                                  String registrationNumber, String maskedRegistration, int seats,
                                  boolean active, boolean luggageFriendly) {
    }

    /** Public view: only the masked registration number is exposed. */
    public record PublicVehicle(VehicleType type, String make, String model, String color,
                                String maskedRegistration, int seats) {
    }

    public record LicenceRequest(@NotBlank String licenceNumber, @NotNull @Future LocalDate expiryDate) {
    }
}
