package com.hopr.controller;

import com.hopr.dto.WalletDtos.TopupRequest;
import com.hopr.dto.WalletDtos.WalletResponse;
import com.hopr.security.AuthUser;
import com.hopr.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponse wallet(@AuthenticationPrincipal AuthUser user) {
        return walletService.view(user.id());
    }

    @PostMapping("/topup")
    public WalletResponse topup(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TopupRequest req) {
        return walletService.topup(user.id(), req.amount());
    }
}
