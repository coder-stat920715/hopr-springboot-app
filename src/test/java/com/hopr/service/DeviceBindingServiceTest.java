package com.hopr.service;

import com.hopr.config.HoprProperties;
import com.hopr.entity.DeviceBindingStatus;
import com.hopr.entity.User;
import com.hopr.entity.UserDeviceBinding;
import com.hopr.exception.DeviceAccountLimitException;
import com.hopr.repository.UserDeviceBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceBindingServiceTest {

    @Mock
    private UserDeviceBindingRepository repo;

    private HoprProperties props;
    private DeviceBindingService service;

    @BeforeEach
    void setUp() {
        props = new HoprProperties();
        service = new DeviceBindingService(repo, props);
    }

    private UserDeviceBinding binding(Long id, String device, String phone) {
        return UserDeviceBinding.builder().id(id).deviceId(device).boundPhoneNumber(phone)
                .status(DeviceBindingStatus.ACTIVE).build();
    }

    @Test
    void blankDeviceIdIsIgnored() {
        assertThatCode(() -> service.assertAllowed(null, "+91 1")).doesNotThrowAnyException();
        assertThatCode(() -> service.assertAllowed("  ", "+91 1")).doesNotThrowAnyException();
        service.bind(null, "m", "o", User.builder().id(1L).phone("p").build());
        service.bind(" ", "m", "o", User.builder().id(1L).phone("p").build());
        verify(repo, never()).save(any());
    }

    @Test
    void unboundDeviceIsAllowed() {
        when(repo.findByDeviceIdAndStatus("d", DeviceBindingStatus.ACTIVE)).thenReturn(List.of());
        assertThatCode(() -> service.assertAllowed("d", "+911")).doesNotThrowAnyException();
    }

    @Test
    void samePhoneIsAllowedOtherPhoneIsBlocked() {
        when(repo.findByDeviceIdAndStatus("d", DeviceBindingStatus.ACTIVE)).thenReturn(List.of(binding(1L, "d", "+911")));
        assertThatCode(() -> service.assertAllowed("d", "+911")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertAllowed("d", "+912")).isInstanceOf(DeviceAccountLimitException.class);
    }

    @Test
    void higherLimitAllowsSecondAccount() {
        props.getSecurity().setMaxAccountsPerDevice(2);
        when(repo.findByDeviceIdAndStatus("d", DeviceBindingStatus.ACTIVE)).thenReturn(List.of(binding(1L, "d", "+911")));
        assertThatCode(() -> service.assertAllowed("d", "+912")).doesNotThrowAnyException();
    }

    @Test
    void bindCreatesOnlyWhenMissing() {
        User u = User.builder().id(5L).phone("+915").build();
        when(repo.findByDeviceIdAndStatus("d", DeviceBindingStatus.ACTIVE)).thenReturn(List.of());
        service.bind(" d ", "Pixel", "ANDROID", u);
        verify(repo).save(any(UserDeviceBinding.class));

        when(repo.findByDeviceIdAndStatus("d", DeviceBindingStatus.ACTIVE)).thenReturn(List.of(binding(2L, "d", "+915")));
        service.bind("d", "Pixel", "ANDROID", u);
        verify(repo).save(any(UserDeviceBinding.class)); // still only the first save
    }

    @Test
    void unbindByAppealReleasesDeviceAndPreviousPhoneBindingsOnce() {
        UserDeviceBinding shared = binding(1L, "d", "+911");
        UserDeviceBinding other = binding(2L, "x", "+911");
        when(repo.findByDeviceIdAndStatus("d", DeviceBindingStatus.ACTIVE)).thenReturn(List.of(shared));
        when(repo.findByBoundPhoneNumberAndStatus("+911", DeviceBindingStatus.ACTIVE)).thenReturn(List.of(shared, other));
        int released = service.unbindByAppeal("d", "+911");
        assertThat(released).isEqualTo(2);
        assertThat(shared.getStatus()).isEqualTo(DeviceBindingStatus.UNBOUND_BY_APPEAL);
        assertThat(other.getUnboundAt()).isNotNull();
    }

    @Test
    void unbindByAppealWithNothingToDo() {
        assertThat(service.unbindByAppeal(null, null)).isZero();
        assertThat(service.unbindByAppeal(" ", " ")).isZero();
    }
}
