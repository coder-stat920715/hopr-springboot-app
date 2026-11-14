package com.hopr.controller;

import com.hopr.dto.BookingDtos;
import com.hopr.dto.BookingDtos.BookingRequest;
import com.hopr.dto.TripDtos.ChatMessageRequest;
import com.hopr.dto.TripDtos.MaskedCallResponse;
import com.hopr.service.BookingChatService;
import com.hopr.dto.BookingDtos.BookingResponse;
import com.hopr.dto.BookingDtos.RespondRequest;
import com.hopr.security.AuthUser;
import com.hopr.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingChatService bookingChatService;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse request(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody BookingRequest req) {
        return bookingService.request(user.id(), BookingService.BookingRequestCommand.from(req));
    }

    @GetMapping("/my")
    public List<BookingResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return bookingService.myBookings(user.id());
    }

    @PatchMapping("/{bookingId}/respond")
    public BookingResponse respond(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId,
                                   @Valid @RequestBody RespondRequest req) {
        return bookingService.respond(user.id(), bookingId, req.action());
    }

    /** Pre-acceptance chat from the Quick Action card. */
    @PostMapping("/{bookingId}/chat")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDtos.BookingChatMessageResponse sendChat(@AuthenticationPrincipal AuthUser user,
                                                           @PathVariable Long bookingId,
                                                           @Valid @RequestBody ChatMessageRequest req) {
        return bookingChatService.send(bookingId, user.id(), req.content());
    }

    @GetMapping("/{bookingId}/chat")
    public List<BookingDtos.BookingChatMessageResponse> chatHistory(@AuthenticationPrincipal AuthUser user,
                                                                    @PathVariable Long bookingId) {
        return bookingChatService.history(bookingId, user.id());
    }

    /** Privacy-masked call bridge between host and guest before the host accepts. */
    @PostMapping("/{bookingId}/masked-call")
    public MaskedCallResponse maskedCall(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId) {
        return bookingChatService.maskedCall(bookingId, user.id());
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long bookingId) {
        return bookingService.cancel(user.id(), bookingId);
    }
}
