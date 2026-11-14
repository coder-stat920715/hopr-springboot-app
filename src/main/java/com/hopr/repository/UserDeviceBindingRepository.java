package com.hopr.repository;

import com.hopr.entity.DeviceBindingStatus;
import com.hopr.entity.UserDeviceBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDeviceBindingRepository extends JpaRepository<UserDeviceBinding, Long> {
    List<UserDeviceBinding> findByDeviceIdAndStatus(String deviceId, DeviceBindingStatus status);

    List<UserDeviceBinding> findByBoundPhoneNumberAndStatus(String phone, DeviceBindingStatus status);

    List<UserDeviceBinding> findByDeviceIdOrderByIdAsc(String deviceId);
}
