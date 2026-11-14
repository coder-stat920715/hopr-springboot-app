package com.hopr.controller;

import com.hopr.dto.HostDtos.ModeDashboardResponse;
import com.hopr.dto.HostDtos.QuickActionsResponse;
import com.hopr.security.AuthUser;
import com.hopr.service.HostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/host")
@RequiredArgsConstructor
public class HostController {

    private final HostService hostService;

    /** "Quick action (N)": prioritised pending guest requests across all upcoming rides of this host. */
    @GetMapping("/quick-actions")
    public QuickActionsResponse quickActions(@AuthenticationPrincipal AuthUser user) {
        return hostService.quickActions(user.id());
    }

    @GetMapping("/dashboard")
    public ModeDashboardResponse dashboard(@AuthenticationPrincipal AuthUser user) {
        return hostService.dashboard(user.id());
    }
}
