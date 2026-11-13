package com.hopr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record SendOtpRequest(@NotBlank String phone) {
    }

    public record SendOtpResponse(String message, long expiresInSeconds, String devOtp) {
    }

    public record VerifyOtpRequest(@NotBlank String phone,
                                   @NotBlank @Size(min = 4, max = 8) String otp,
                                   String fullName) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                                long expiresInSeconds, boolean newUser, UserDtos.UserProfile user) {
    }
}
