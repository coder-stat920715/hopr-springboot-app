package com.hopr.websocket;

import com.hopr.security.AuthUser;
import com.hopr.security.JwtService;
import com.hopr.service.TripAccessService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/** Authenticates STOMP CONNECT frames with the JWT and authorises trip topic subscriptions. */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TRIP_TOPIC = "/topic/trips/";

    private final JwtService jwtService;
    private final TripAccessService tripAccess;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Missing Authorization header on STOMP CONNECT");
            }
            Claims claims = jwtService.parse(header.substring(7));
            AuthUser user = jwtService.toAuthUser(claims);
            List<SimpleGrantedAuthority> authorities = user.roles().stream().map(SimpleGrantedAuthority::new).toList();
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, authorities));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(TRIP_TOPIC)) {
                if (!(accessor.getUser() instanceof Authentication auth) || !(auth.getPrincipal() instanceof AuthUser user)) {
                    throw new MessagingException("Not authenticated");
                }
                String rest = destination.substring(TRIP_TOPIC.length());
                String idPart = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                long rideId;
                try {
                    rideId = Long.parseLong(idPart);
                } catch (NumberFormatException ex) {
                    throw new MessagingException("Invalid trip topic");
                }
                if (!tripAccess.isParticipant(rideId, user.id())) {
                    throw new MessagingException("You are not a participant of this trip");
                }
            }
        }
        return message;
    }
}
