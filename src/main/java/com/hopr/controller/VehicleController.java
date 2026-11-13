package com.hopr.controller;

import com.hopr.dto.CommonDtos.MessageResponse;
import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.dto.VehicleDtos.LicenceRequest;
import com.hopr.dto.VehicleDtos.VehicleRequest;
import com.hopr.dto.VehicleDtos.VehicleResponse;
import com.hopr.security.AuthUser;
import com.hopr.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public List<VehicleResponse> list(@AuthenticationPrincipal AuthUser user) {
        return vehicleService.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse add(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody VehicleRequest req) {
        return vehicleService.add(user.id(), req);
    }

    @PostMapping("/{vehicleId}/activate")
    public VehicleResponse activate(@AuthenticationPrincipal AuthUser user, @PathVariable Long vehicleId) {
        return vehicleService.activate(user.id(), vehicleId);
    }

    @DeleteMapping("/{vehicleId}")
    public MessageResponse remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long vehicleId) {
        vehicleService.remove(user.id(), vehicleId);
        return new MessageResponse("Vehicle removed");
    }

    @PostMapping("/licence")
    public UserProfile submitLicence(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody LicenceRequest req) {
        return vehicleService.submitLicence(user.id(), req);
    }
}
