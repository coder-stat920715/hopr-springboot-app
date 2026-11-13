package com.hopr.controller;

import com.hopr.dto.AuthDtos.RefreshRequest;
import com.hopr.dto.AuthDtos.SendOtpRequest;
import com.hopr.dto.AuthDtos.SendOtpResponse;
import com.hopr.dto.AuthDtos.TokenResponse;
import com.hopr.dto.AuthDtos.VerifyOtpRequest;
import com.hopr.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-otp")
    public SendOtpResponse sendOtp(@Valid @RequestBody SendOtpRequest req) {
        return authService.sendOtp(req);
    }

    @PostMapping("/verify-otp")
    public TokenResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return authService.verifyOtp(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }
}
