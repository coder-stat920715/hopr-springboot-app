package com.hopr.controller;

import com.hopr.dto.AppealDtos.AppealResponse;
import com.hopr.dto.AppealDtos.ResolveAppealRequest;
import com.hopr.security.AuthUser;
import com.hopr.service.AppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN only (enforced in SecurityConfig for /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin/appeals")
@RequiredArgsConstructor
public class AdminAppealController {

    private final AppealService appealService;

    @GetMapping
    public List<AppealResponse> list() {
        return appealService.list();
    }

    @GetMapping("/{appealId}")
    public AppealResponse get(@PathVariable Long appealId) {
        return appealService.get(appealId);
    }

    @PostMapping("/{appealId}/resolve")
    public AppealResponse resolve(@AuthenticationPrincipal AuthUser admin, @PathVariable Long appealId,
                                  @Valid @RequestBody ResolveAppealRequest req) {
        return appealService.resolve(admin.id(), appealId, req);
    }
}
