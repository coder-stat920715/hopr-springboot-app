package com.hopr.service;

import com.hopr.exception.ForbiddenException;
import com.hopr.exception.ResourceNotFoundException;
import com.hopr.entity.Booking;
import com.hopr.entity.BookingStatus;
import com.hopr.entity.Ride;
import com.hopr.entity.User;
import com.hopr.repository.BookingRepository;
import com.hopr.repository.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripAccessServiceTest {

    @Mock
    private RideRepository rides;
    @Mock
    private BookingRepository bookings;

    private TripAccessService service;
    private Ride ride;

    @BeforeEach
    void setUp() {
        service = new TripAccessService(rides, bookings);
        User host = User.builder().id(1L).build();
        ride = Ride.builder().id(10L).host(host).build();
        Booking confirmed = Booking.builder().id(100L).ride(ride).guest(User.builder().id(2L).build())
                .status(BookingStatus.CONFIRMED).build();
        Booking pending = Booking.builder().id(101L).ride(ride).guest(User.builder().id(3L).build())
                .status(BookingStatus.PENDING).build();
        when(rides.findById(10L)).thenReturn(Optional.of(ride));
        when(bookings.findByRideIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(confirmed, pending));
        when(bookings.findById(100L)).thenReturn(Optional.of(confirmed));
    }

    @Test
    void participantsAreHostAndConfirmedGuests() {
        assertThat(service.participantIds(10L)).containsExactly(1L, 2L);
        assertThat(service.isParticipant(10L, 2L)).isTrue();
        assertThat(service.isParticipant(10L, 3L)).isFalse();
        assertThat(service.requireParticipant(10L, 1L)).isSameAs(ride);
    }

    @Test
    void unknownRideAndOutsiders() {
        assertThat(service.isParticipant(99L, 1L)).isFalse();
        assertThatThrownBy(() -> service.participantIds(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.requireParticipant(99L, 1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.requireParticipant(10L, 3L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void bookingParticipants() {
        assertThat(service.isBookingParticipant(100L, 2L)).isTrue();
        assertThat(service.isBookingParticipant(100L, 1L)).isTrue();
        assertThat(service.isBookingParticipant(100L, 9L)).isFalse();
        assertThat(service.isBookingParticipant(999L, 1L)).isFalse();
    }
}
