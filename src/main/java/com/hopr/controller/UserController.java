package com.hopr.controller;

import com.hopr.dto.AuthDtos.SendOtpResponse;
import com.hopr.dto.UserDtos.CorporateEmailRequest;
import com.hopr.dto.UserDtos.OtpRequest;
import com.hopr.dto.UserDtos.UpdateProfileRequest;
import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.security.AuthUser;
import com.hopr.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal AuthUser user) {
        return userService.me(user.id());
    }

    @PutMapping("/profile")
    public UserProfile updateProfile(@AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(user.id(), req);
    }

    @PostMapping("/corporate-email/send-otp")
    public SendOtpResponse sendCorporateOtp(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody CorporateEmailRequest req) {
        return userService.sendCorporateEmailOtp(user.id(), req);
    }

    @PostMapping("/corporate-email/verify")
    public UserProfile verifyCorporateEmail(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody OtpRequest req) {
        return userService.verifyCorporateEmail(user.id(), req.otp());
    }

    @PostMapping("/company-change/initiate")
    public SendOtpResponse initiateCompanyChange(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody CorporateEmailRequest req) {
        return userService.initiateCompanyChange(user.id(), req);
    }

    @PostMapping("/company-change/verify")
    public UserProfile verifyCompanyChange(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody OtpRequest req) {
        return userService.verifyCompanyChange(user.id(), req.otp());
    }
}
