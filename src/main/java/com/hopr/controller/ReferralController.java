package com.hopr.controller;

import com.hopr.dto.WalletDtos.ApplyReferralRequest;
import com.hopr.dto.WalletDtos.ReferralSummary;
import com.hopr.security.AuthUser;
import com.hopr.service.ReferralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping
    public ReferralSummary summary(@AuthenticationPrincipal AuthUser user) {
        return referralService.summary(user.id());
    }

    @PostMapping("/apply")
    public ReferralSummary apply(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ApplyReferralRequest req) {
        return referralService.apply(user.id(), req.code());
    }
}
