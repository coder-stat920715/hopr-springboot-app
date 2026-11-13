package com.hopr.service;

import com.hopr.dto.UserDtos.UserProfile;
import com.hopr.dto.VehicleDtos.LicenceRequest;
import com.hopr.dto.VehicleDtos.VehicleRequest;
import com.hopr.dto.VehicleDtos.VehicleResponse;
import com.hopr.entity.RideTemplate;
import com.hopr.entity.User;
import com.hopr.entity.Vehicle;
import com.hopr.entity.VehicleType;
import com.hopr.exception.BadRequestException;
import com.hopr.exception.ConflictException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.repository.RideTemplateRepository;
import com.hopr.repository.UserRepository;
import com.hopr.repository.VehicleRepository;
import com.hopr.util.VehicleMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final Pattern LICENCE = Pattern.compile("^[A-Z]{2}\\d{2}\\d{4}\\d{7}$");

    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final RideTemplateRepository templates;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(Long userId) {
        return vehicles.findByOwnerIdAndRemovedFalse(userId).stream().map(mapper::vehicle).toList();
    }

    @Transactional
    public VehicleResponse add(Long userId, VehicleRequest req) {
        User owner = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (req.type() == VehicleType.BIKE && req.seats() != 1) {
            throw new BadRequestException("A bike / 2-wheeler offers exactly 1 pillion seat");
        }
        String reg = VehicleMasker.normalize(req.registrationNumber());
        if (vehicles.existsByRegistrationNumber(reg)) {
            throw new ConflictException("Vehicle with this registration number is already registered");
        }
        boolean first = vehicles.countByOwnerIdAndRemovedFalse(userId) == 0;
        Vehicle v = vehicles.save(Vehicle.builder()
                .owner(owner)
                .vehicleType(req.type())
                .make(req.make())
                .model(req.model())
                .color(req.color())
                .registrationNumber(reg)
                .seats(req.seats())
                .luggageFriendly(req.luggageFriendly())
                .active(first)
                .build());
        return mapper.vehicle(v);
    }

    /** Switches the owner's active (default) vehicle. */
    @Transactional
    public VehicleResponse activate(Long userId, Long vehicleId) {
        Vehicle target = vehicles.findByIdAndOwnerIdAndRemovedFalse(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        for (Vehicle v : vehicles.findByOwnerIdAndRemovedFalse(userId)) {
            v.setActive(v.getId().equals(target.getId()));
            vehicles.save(v);
        }
        return mapper.vehicle(target);
    }

    @Transactional
    public void remove(Long userId, Long vehicleId) {
        Vehicle v = vehicles.findByIdAndOwnerIdAndRemovedFalse(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        for (RideTemplate t : templates.findByHostIdAndActiveTrue(userId)) {
            if (t.getVehicle().getId().equals(vehicleId)) {
                throw new ConflictException("Vehicle is used by an active recurring commute. Switch vehicle first.");
            }
        }
        v.setRemoved(true);
        boolean wasActive = v.isActive();
        v.setActive(false);
        vehicles.save(v);
        if (wasActive) {
            vehicles.findByOwnerIdAndRemovedFalse(userId).stream().findFirst().ifPresent(other -> {
                other.setActive(true);
                vehicles.save(other);
            });
        }
    }

    @Transactional
    public UserProfile submitLicence(Long userId, LicenceRequest req) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String number = req.licenceNumber().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!LICENCE.matcher(number).matches()) {
            throw new BadRequestException("Invalid driving licence number format (expected e.g. TS0720190012345)");
        }
        u.setLicenceNumber(number);
        u.setLicenceExpiry(req.expiryDate());
        // Simulated KYC: a well-formed, unexpired licence is auto-approved.
        u.setLicenceVerified(true);
        return mapper.profile(users.save(u));
    }
}
