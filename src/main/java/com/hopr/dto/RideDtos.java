package com.hopr.dto;

import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.dto.CommonDtos.PublicUser;
import com.hopr.dto.VehicleDtos.PublicVehicle;
import com.hopr.entity.CommuteDirection;
import com.hopr.entity.RideStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public final class RideDtos {
    private RideDtos() {
    }

    public record TemplateRequest(@NotNull CommuteDirection direction, @NotNull Long vehicleId,
                                  @NotNull @Valid LocationDto start, @NotNull @Valid LocationDto end,
                                  @NotNull LocalTime departureTime, Set<DayOfWeek> days,
                                  @Min(1) @Max(4) Integer seats, String city) {
    }

    public record TemplateResponse(Long id, CommuteDirection direction, LocationDto start, LocationDto end,
                                   LocalTime departureTime, Set<DayOfWeek> days, Long vehicleId, int seats,
                                   String city, boolean active) {
    }

    public record CreateRideRequest(@NotNull CommuteDirection direction, @NotNull Long vehicleId,
                                    @NotNull @Valid LocationDto start, @NotNull @Valid LocationDto end,
                                    @NotNull LocalDate date, @NotNull LocalTime departureTime,
                                    @Min(1) @Max(4) Integer seats) {
    }

    public record UpdateRideRequest(@Valid LocationDto start, @Valid LocationDto end, LocalTime departureTime,
                                    Long vehicleId, @Min(1) @Max(4) Integer seats) {
    }

    public record RideResponse(Long id, Long templateId, CommuteDirection direction, LocationDto start,
                               LocationDto end, LocalDate date, LocalTime departureTime, int totalSeats,
                               int seatsLeft, RideStatus status, PublicUser host, PublicVehicle vehicle,
                               BigDecimal ratePerKm, double routeDistanceKm, String city, boolean customized) {
    }

    public record MigrateRequest(String newCity, @Valid LocationDto home, @Valid LocationDto office,
                                 LocalTime homeToOfficeTime, LocalTime officeToHomeTime, Set<DayOfWeek> days) {
    }

    public record MigrateResponse(int archivedTemplates, int cancelledRides, int generatedRides,
                                  List<TemplateResponse> newTemplates) {
    }

    public record MatchCard(Long rideId, PublicUser host, PublicVehicle vehicle, CommuteDirection direction,
                            LocalDate date, LocalTime departureTime, int matchPercentage,
                            int pickupWalkMeters, double sharedRideKm, int dropWalkMeters,
                            String pickupWindow, LocalTime pickupWindowStart, LocalTime pickupWindowEnd,
                            int seatsLeft, BigDecimal standardFare, BigDecimal discountedFare,
                            List<String> tags, LocationDto hostStart, LocationDto hostEnd,
                            LocationDto pickupPoint, LocationDto dropPoint) {
    }
}
