package com.hopr.controller;

import com.hopr.dto.TripDtos.ChatMessageRequest;
import com.hopr.dto.TripDtos.ChatMessageResponse;
import com.hopr.dto.TripDtos.LocationResponse;
import com.hopr.dto.TripDtos.LocationUpdateRequest;
import com.hopr.dto.TripDtos.MaskedCallRequest;
import com.hopr.dto.TripDtos.MaskedCallResponse;
import com.hopr.dto.TripDtos.RatingRequest;
import com.hopr.dto.TripDtos.RatingResponse;
import com.hopr.security.AuthUser;
import com.hopr.service.RatingService;
import com.hopr.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips/{rideId}")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final RatingService ratingService;

    @GetMapping("/chat")
    public List<ChatMessageResponse> chatHistory(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId) {
        return tripService.history(rideId, user.id());
    }

    /** REST fallback for sending a chat message (also broadcast to the STOMP topic). */
    @PostMapping("/chat")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse sendChat(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId,
                                        @Valid @RequestBody ChatMessageRequest req) {
        return tripService.sendChat(rideId, user.id(), req.content());
    }

    @PostMapping("/masked-call")
    public MaskedCallResponse maskedCall(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId,
                                         @Valid @RequestBody MaskedCallRequest req) {
        return tripService.maskedCall(rideId, user.id(), req.targetUserId());
    }

    @PostMapping("/location")
    public LocationResponse updateLocation(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId,
                                           @Valid @RequestBody LocationUpdateRequest req) {
        return tripService.updateLocation(rideId, user.id(), req);
    }

    @GetMapping("/location")
    public LocationResponse location(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId) {
        return tripService.getLocation(rideId, user.id());
    }

    @PostMapping("/rate")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse rate(@AuthenticationPrincipal AuthUser user, @PathVariable Long rideId,
                               @Valid @RequestBody RatingRequest req) {
        return ratingService.rate(user.id(), rideId, req);
    }
}
