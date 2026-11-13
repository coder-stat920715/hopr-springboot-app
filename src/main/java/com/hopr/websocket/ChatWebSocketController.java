package com.hopr.websocket;

import com.hopr.dto.TripDtos.ChatMessageRequest;
import com.hopr.security.AuthUser;
import com.hopr.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP trip group chat. Clients connect to /ws (Authorization: Bearer ... header on CONNECT),
 * subscribe to /topic/trips/{rideId} and send to /app/trips/{rideId}/chat with body {"content": "..."}.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final TripService tripService;

    @MessageMapping("/trips/{rideId}/chat")
    public void chat(@DestinationVariable Long rideId, @Payload ChatMessageRequest request, Principal principal) {
        AuthUser user = (AuthUser) ((Authentication) principal).getPrincipal();
        tripService.sendChat(rideId, user.id(), request.content());
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleError(Exception ex) {
        return ex.getMessage();
    }
}
