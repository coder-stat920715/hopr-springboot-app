package com.hopr.repository;

import com.hopr.entity.BookingMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingMessageRepository extends JpaRepository<BookingMessage, Long> {
    List<BookingMessage> findByBookingIdOrderBySentAtAscIdAsc(Long bookingId);
}
