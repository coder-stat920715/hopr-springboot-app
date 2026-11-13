package com.hopr.dto;

import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.dto.CommonDtos.PublicUser;
import com.hopr.dto.VehicleDtos.PublicVehicle;
import com.hopr.entity.BookingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class BookingDtos {
    private BookingDtos() {
    }

    public enum BookingAction {ACCEPT, REJECT}

    public record BookingRequest(@NotNull Long rideId, @NotNull @Valid LocationDto pickup,
                                 @NotNull @Valid LocationDto drop) {
    }

    public record RespondRequest(@NotNull BookingAction action) {
    }

    public record BookingResponse(Long id, Long rideId, BookingStatus status, LocalDate rideDate,
                                  LocalTime departureTime, PublicUser host, PublicUser guest,
                                  PublicVehicle vehicle, LocationDto pickup, LocationDto drop,
                                  int pickupWalkMeters, double sharedRideKm, int dropWalkMeters,
                                  int matchPercentage, String pickupWindow, BigDecimal standardFare,
                                  BigDecimal discountedFare, boolean escrowHeld, Instant createdAt) {
    }
}
