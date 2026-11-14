package com.hopr.dto;

import com.hopr.entity.CommuteMode;
import com.hopr.entity.FareTrend;
import com.hopr.entity.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class HostDtos {
    private HostDtos() {
    }

    public record HostMetrics(BigDecimal fuelCostRecovered, long completedPassengerTrips, int activeSeatsPublished,
                              int seatsStillAvailable, int upcomingRidesPublished, int pendingQuickActions) {
    }

    public record GuestMetrics(long upcomingBookings, long pendingRequests, BigDecimal walletBalance,
                               BigDecimal escrowBalance) {
    }

    public record ModeDashboardResponse(CommuteMode mode, HostMetrics host, GuestMetrics guest) {
    }

    public record QuickActionCard(Long bookingId, Long rideId, String rideDate, LocalDate rideDateIso,
                                  String hostRideTime, Long guestId, String guestName, String guestPhotoUrl,
                                  String guestCompanyName, boolean corporateVerified,
                                  LocalTime guestPreferredDepartureTime, String guestPreferredDepartureLabel,
                                  LocalTime hostEstimatedArrivalAtPickup, String hostEstimatedArrivalLabel,
                                  int pickupWalkMeters, String pickupWalkLabel, double sharedRideKm,
                                  String sharedRideLabel, VehicleType vehicleType, int dropWalkMeters,
                                  String dropWalkLabel, BigDecimal baseFare, BigDecimal dynamicFare,
                                  FareTrend fareTrend, String fareLabel, int matchPercentage, String matchLabel,
                                  Instant requestedAt) {
    }

    public record QuickActionsResponse(int count, String label, List<QuickActionCard> requests) {
    }
}
