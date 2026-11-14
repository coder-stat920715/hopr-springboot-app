package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.DeviceBindingStatus;
import com.hopr.entity.User;
import com.hopr.entity.UserDeviceBinding;
import com.hopr.exception.DeviceAccountLimitException;
import com.hopr.repository.UserDeviceBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One physical device may be actively bound to at most {@code hopr.security.max-accounts-per-device} phone numbers. */
@Service
@RequiredArgsConstructor
public class DeviceBindingService {

    private final UserDeviceBindingRepository bindings;
    private final HoprProperties props;

    /** Throws {@link DeviceAccountLimitException} when the device is already bound to a different phone number. */
    @Transactional(readOnly = true)
    public void assertAllowed(String deviceId, String phone) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        List<UserDeviceBinding> active = bindings.findByDeviceIdAndStatus(deviceId.trim(), DeviceBindingStatus.ACTIVE);
        Set<String> phones = new HashSet<>();
        for (UserDeviceBinding b : active) {
            if (phone.equals(b.getBoundPhoneNumber())) {
                return; // same account on its own device
            }
            phones.add(b.getBoundPhoneNumber());
        }
        if (phones.size() >= props.getSecurity().getMaxAccountsPerDevice()) {
            throw new DeviceAccountLimitException();
        }
    }

    @Transactional
    public void bind(String deviceId, String model, String os, User user) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        String id = deviceId.trim();
        boolean already = bindings.findByDeviceIdAndStatus(id, DeviceBindingStatus.ACTIVE).stream()
                .anyMatch(b -> user.getPhone().equals(b.getBoundPhoneNumber()));
        if (already) {
            return;
        }
        bindings.save(UserDeviceBinding.builder()
                .deviceId(id)
                .deviceModel(model)
                .osPlatform(os)
                .boundUserId(user.getId())
                .boundPhoneNumber(user.getPhone())
                .status(DeviceBindingStatus.ACTIVE)
                .build());
    }

    /**
     * Releases the conflicting bindings of an approved appeal: every active binding of the blocked device and
     * every active binding of the previously registered phone number. Returns the number of bindings released.
     */
    @Transactional
    public int unbindByAppeal(String deviceId, String previousPhone) {
        Set<Long> seen = new HashSet<>();
        int released = 0;
        if (deviceId != null && !deviceId.isBlank()) {
            for (UserDeviceBinding b : bindings.findByDeviceIdAndStatus(deviceId.trim(), DeviceBindingStatus.ACTIVE)) {
                released += release(b, seen);
            }
        }
        if (previousPhone != null && !previousPhone.isBlank()) {
            for (UserDeviceBinding b : bindings.findByBoundPhoneNumberAndStatus(previousPhone, DeviceBindingStatus.ACTIVE)) {
                released += release(b, seen);
            }
        }
        return released;
    }

    private int release(UserDeviceBinding b, Set<Long> seen) {
        if (!seen.add(b.getId())) {
            return 0;
        }
        b.setStatus(DeviceBindingStatus.UNBOUND_BY_APPEAL);
        b.setUnboundAt(Instant.now());
        bindings.save(b);
        return 1;
    }
}
