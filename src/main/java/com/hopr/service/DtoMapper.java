package com.hopr.service;

import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.dto.CommonDtos.CorporateBadge;
import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.dto.CommonDtos.PublicUser;
import com.hopr.dto.RideDtos.RideResponse;
import com.hopr.dto.RideDtos.TemplateResponse;
import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.dto.VehicleDtos.PublicVehicle;
import com.hopr.dto.VehicleDtos.VehicleResponse;
import com.hopr.entity.Booking;
import com.hopr.entity.CompanyStatus;
import com.hopr.entity.Ride;
import com.hopr.entity.RideTemplate;
import com.hopr.entity.User;
import com.hopr.entity.Vehicle;
import com.hopr.util.GeoUtil;
import com.hopr.util.VehicleMasker;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;

@Component
public class DtoMapper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    public static String window(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return null;
        }
        return TIME_FMT.format(start) + " - " + TIME_FMT.format(end);
    }

    public CorporateBadge badge(User u) {
        if (u.isCorporateVerified() && u.getCompanyStatus() == CompanyStatus.VERIFIED) {
            return new CorporateBadge(true, u.getVerifiedCompanyName(), CompanyStatus.VERIFIED.name(), "GREEN");
        }
        if (u.getCompanyStatus() == CompanyStatus.PENDING_REVERIFICATION) {
            return new CorporateBadge(false, null, CompanyStatus.PENDING_REVERIFICATION.name(), "AMBER");
        }
        return new CorporateBadge(false, null, CompanyStatus.NONE.name(), "GREY");
    }

    public PublicUser publicUser(User u) {
        return new PublicUser(u.getId(), u.getFullName(), u.getProfilePhotoUrl(), badge(u),
                GeoUtil.round1(u.getRatingAverage()), u.getRatingCount());
    }

    public UserProfile profile(User u) {
        return new UserProfile(u.getId(), u.getPhone(), u.getFullName(), u.getProfilePhotoUrl(), u.getCity(),
                u.getCommuteMode(), LocationDto.from(u.getHome()), LocationDto.from(u.getOffice()),
                u.getCorporateEmail(), badge(u), u.isLicenceVerified(), GeoUtil.round1(u.getRatingAverage()),
                u.getRatingCount(), u.getReferralCode(), new HashSet<>(u.getRoles()));
    }

    public VehicleResponse vehicle(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getVehicleType(), v.getMake(), v.getModel(), v.getColor(),
                v.getRegistrationNumber(), VehicleMasker.mask(v.getRegistrationNumber()), v.getSeats(),
                v.isActive(), v.isLuggageFriendly());
    }

    /** Public vehicle view: registration number is always masked. */
    public PublicVehicle publicVehicle(Vehicle v) {
        return new PublicVehicle(v.getVehicleType(), v.getMake(), v.getModel(), v.getColor(),
                VehicleMasker.mask(v.getRegistrationNumber()), v.getSeats());
    }

    public RideResponse ride(Ride r) {
        return new RideResponse(r.getId(), r.getTemplateId(), r.getDirection(), LocationDto.from(r.getStart()),
                LocationDto.from(r.getEnd()), r.getRideDate(), r.getDepartureTime(), r.getTotalSeats(),
                r.getSeatsLeft(), r.getStatus(), publicUser(r.getHost()), publicVehicle(r.getVehicle()),
                r.getRatePerKm(), r.getRouteDistanceKm(), r.getCity(), r.isCustomized());
    }

    public TemplateResponse template(RideTemplate t) {
        return new TemplateResponse(t.getId(), t.getDirection(), LocationDto.from(t.getStart()),
                LocationDto.from(t.getEnd()), t.getDepartureTime(), new HashSet<>(t.getDaysOfWeek()),
                t.getVehicle().getId(), t.getSeats(), t.getCity(), t.isActive());
    }

    public BookingResponse booking(Booking b) {
        Ride r = b.getRide();
        return new BookingResponse(b.getId(), r.getId(), b.getStatus(), r.getRideDate(), r.getDepartureTime(),
                publicUser(r.getHost()), publicUser(b.getGuest()), publicVehicle(r.getVehicle()),
                LocationDto.from(b.getPickup()), LocationDto.from(b.getDropPoint()), b.getPickupWalkMeters(),
                b.getSharedRideKm(), b.getDropWalkMeters(), b.getMatchPercentage(),
                window(b.getPickupWindowStart(), b.getPickupWindowEnd()), b.getStandardFare(),
                b.getDiscountedFare(), b.isEscrowHeld(), b.getCreatedAt());
    }
}
