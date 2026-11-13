package com.hopr.controller;

import com.hopr.dto.SupportDtos.TicketRequest;
import com.hopr.dto.SupportDtos.TicketResponse;
import com.hopr.dto.SupportDtos.TicketUpdateRequest;
import com.hopr.dto.UserDtos.CompanyResetRequest;
import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.security.AuthUser;
import com.hopr.service.SupportService;
import com.hopr.service.UserService;
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
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;
    private final UserService userService;

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TicketRequest req) {
        return supportService.create(user.id(), req);
    }

    @GetMapping("/tickets")
    public List<TicketResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return supportService.mine(user.id());
    }

    /** ADMIN: every ticket in the system. */
    @GetMapping("/tickets/all")
    public List<TicketResponse> all() {
        return supportService.all();
    }

    /** ADMIN: answer / change status of a ticket. */
    @PatchMapping("/tickets/{ticketId}/respond")
    public TicketResponse respond(@PathVariable Long ticketId, @RequestBody TicketUpdateRequest req) {
        return supportService.respond(ticketId, req);
    }

    /** ADMIN: support-assisted reset of a locked company affiliation. */
    @PostMapping("/company-reset")
    public UserProfile companyReset(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody CompanyResetRequest req) {
        return userService.adminCompanyReset(admin.id(), req);
    }
}
