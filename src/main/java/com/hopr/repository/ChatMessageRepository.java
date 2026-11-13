package com.hopr.repository;

import com.hopr.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRideIdOrderBySentAtAscIdAsc(Long rideId);
}
