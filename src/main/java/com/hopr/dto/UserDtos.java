package com.hopr.dto;

import com.hopr.dto.CommonDtos.CorporateBadge;
import com.hopr.dto.CommonDtos.LocationDto;
import com.hopr.entity.CommuteMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserProfile(Long id, String phone, String fullName, String profilePhotoUrl, String city,
                              CommuteMode commuteMode, LocationDto home, LocationDto office,
                              String corporateEmail, CorporateBadge corporateBadge,
                              boolean licenceVerified, double ratingAverage, int ratingCount,
                              String referralCode, Set<String> roles) {
    }

    public record UpdateProfileRequest(String fullName, String profilePhotoUrl, String city,
                                       @Valid LocationDto home, @Valid LocationDto office,
                                       CommuteMode commuteMode) {
    }

    public record CorporateEmailRequest(@NotBlank @Email String corporateEmail) {
    }

    public record OtpRequest(@NotBlank String otp) {
    }

    public record CompanyResetRequest(@NotNull Long userId, String reason, Long ticketId) {
    }
}
