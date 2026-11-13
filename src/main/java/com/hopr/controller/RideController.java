package com.hopr.controller;

import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.dto.CommonDtos.MessageResponse;
import com.hopr.dto.RideDtos.CreateRideRequest;
import com.hopr.dto.RideDtos.MatchCard;
import com.hopr.dto.RideDtos.MigrateRequest;
import com.hopr.dto.RideDtos.MigrateResponse;
import com.hopr.dto.RideDtos.RideResponse;
import com.hopr.dto.RideDtos.TemplateRequest;
import com.hopr.dto.RideDtos.TemplateResponse;
import com.hopr.dto.RideDtos.UpdateRideRequest;
import com.hopr.entity.CommuteDirection;
import com.hopr.entity.GeoPoint;
import com.hopr.entity.User;
import com.hopr.exception.BadRequestException;
import com.hopr.security.AuthUser;
import com.hopr.service.BookingService;
import com.hopr.service.RideMatchingService;
import com.hopr.service.RideService;
import com.hopr.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;
    private final RideMatchingService matchingService;
    private final BookingService bookingService;
    private final UserService userService;

    // ---------------------------------------------------------------- recurring planner

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse createTemplate(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody TemplateRequest req) {
        return rideService.createTemplate(user.id(), req);
    }

    @GetMapping("/templates")
    public List<TemplateResponse> templates(@AuthenticationPrincipal AuthUser user) {
        return rideService.listTemplates(user.id());
    }

    @DeleteMapping("/templates/{templateId}")
    public MessageResponse archiveTemplate(@AuthenticationPrincipal AuthUser user, @PathVariable Long templateId) {
        rideService.archiveTemplate(user.id(), templateId);
        return new MessageResponse("Recurring commute archived");
    }

    @PostMapping("/migrate-city-schedules")
    public MigrateResponse migrate(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody MigrateRequest req) {
        return rideService.migrateCitySchedules(user.id(), req);
    }

    // ------------------------------------------------------------------- single rides

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RideResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateRideRequest req) {
        return rideService.createRide(user.id(), req);
    }

    @GetMapping("/my")
    public List<RideResponse> myRides(@AuthenticationPrincipal AuthUser user,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return rideService.myRides(user.id(), date);
    }

    /** Guest search: Host Match Cards with walk-and-ride breakdown. */
    @GetMapping("/matches")
    public List<MatchCard> matches(@AuthenticationPrincipal AuthUser user,
                                   @RequestParam(required = false) Double startLat,
                                   @RequestParam(required = false) Double startLng,
                                   @RequestParam(required = false) Double endLat,
                                   @RequestParam(required = false) Double endLng,
                                   @RequestParam(defaultValue = "HOME_TO_OFFICE") CommuteDirection direction,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
                                   @RequestParam(required = false) Integer windowMinutes,
                                   @RequestParam(defaultValue = "false") boolean carOnly,
                                   @RequestParam(required = false) Integer minMatch,
                                   @RequestParam(required = false) String city) {
        GeoPoint start;
        GeoPoint end;
        boolean anyCoord = startLat != null || startLng != null || endLat != null || endLng != null;
        if (anyCoord) {
            if (startLat == null || startLng == null || endLat == null || endLng == null) {
                throw new BadRequestException("Provide all of startLat, startLng, endLat, endLng (or none to use saved locations)");
            }
            start = GeoPoint.of("Start", startLat, startLng);
            end = GeoPoint.of("End", endLat, endLng);
        } else {
            User u = userService.getUser(user.id());
            GeoPoint home = u.getHome();
            GeoPoint office = u.getOffice();
            if (home == null || home.getLat() == null || office == null || office.getLat() == null) {
                throw new BadRequestException("Save home and office locations in your profile or pass coordinates");
            }
            start = direction == CommuteDirection.HOME_TO_OFFICE ? home : office;
            end = direction == CommuteDirection.HOME_TO_OFFICE ? office : home;
        }
        return matchingService.findMatches(user.id(),
                new RideMatchingService.Criteria(start, end, date, time, windowMinutes, carOnly, minMatch, city));
    }

    @GetMapping("/{rideId}")
    public RideResponse get(@PathVariable Long rideId) {
        return rideService.getRide(rideId);
    }

    @PutMapping("/{rideId}")
    public RideResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId,
                               @Valid @RequestBody UpdateRideRequest req) {
        return rideService.updateRide(user.id(), rideId, req);
    }

    @PostMapping("/{rideId}/swap-direction")
    public RideResponse swap(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId,
                             @RequestParam(defaultValue = "UPDATE") RideService.SwapMode mode,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime returnTime) {
        return rideService.swapDirection(user.id(), rideId, mode, returnTime);
    }

    @GetMapping("/{rideId}/requests")
    public List<BookingResponse> requests(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId) {
        return bookingService.hostRequests(user.id(), rideId);
    }

    @PostMapping("/{rideId}/start")
    public RideResponse start(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId) {
        return rideService.startRide(user.id(), rideId);
    }

    @PostMapping("/{rideId}/complete")
    public RideResponse complete(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId) {
        return rideService.completeRide(user.id(), rideId);
    }

    @PostMapping("/{rideId}/cancel")
    public RideResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId) {
        return rideService.cancelRide(user.id(), rideId);
    }
}
