package com.hopr.websocket;

import com.hopr.config.HoprProperties;
import com.hopr.dto.TripDtos.ChatMessageRequest;
import com.hopr.entity.User;
import com.hopr.security.AuthUser;
import com.hopr.security.JwtService;
import com.hopr.service.TripAccessService;
import com.hopr.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompInterceptorAndControllerTest {

    @Mock
    private TripAccessService access;
    @Mock
    private TripService tripService;

    private JwtService jwt;
    private StompAuthChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwt = new JwtService(new HoprProperties());
        interceptor = new StompAuthChannelInterceptor(jwt, access);
    }

    private Message<byte[]> message(StompHeaderAccessor acc) {
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            acc.setNativeHeader("Authorization", authorization);
        }
        return acc;
    }

    private StompHeaderAccessor subscribe(String destination, boolean authenticated) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setSubscriptionId("s1");
        acc.setDestination(destination);
        if (authenticated) {
            acc.setUser(new UsernamePasswordAuthenticationToken(new AuthUser(7L, "+91", Set.of("ROLE_USER")), null));
        }
        return acc;
    }

    @Test
    void connectAuthenticatesWithJwt() {
        User u = User.builder().id(7L).phone("+91").roles(Set.of("ROLE_USER")).build();
        StompHeaderAccessor acc = connect("Bearer " + jwt.createAccessToken(u));
        interceptor.preSend(message(acc), channel);
        assertThat(acc.getUser()).isNotNull();
    }

    @Test
    void connectWithoutValidTokenFails() {
        assertThatThrownBy(() -> interceptor.preSend(message(connect(null)), channel)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(connect("Basic x")), channel)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(connect("Bearer garbage")), channel)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void tripSubscriptionsAreAuthorised() {
        when(access.isParticipant(5L, 7L)).thenReturn(true);
        when(access.isParticipant(6L, 7L)).thenReturn(false);
        Message<byte[]> ok = message(subscribe("/topic/trips/5", true));
        assertThat(interceptor.preSend(ok, channel)).isSameAs(ok);
        interceptor.preSend(message(subscribe("/topic/trips/5/location", true)), channel);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/trips/6", true)), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/trips/abc", true)), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/trips/5", false)), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void bookingSubscriptionsAreAuthorised() {
        when(access.isBookingParticipant(8L, 7L)).thenReturn(true);
        when(access.isBookingParticipant(9L, 7L)).thenReturn(false);
        interceptor.preSend(message(subscribe("/topic/bookings/8", true)), channel);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/bookings/9", true)), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/bookings/x", true)), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/bookings/8", false)), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void otherFramesPassThrough() {
        Message<byte[]> other = message(subscribe("/topic/other", true));
        assertThat(interceptor.preSend(other, channel)).isSameAs(other);
        Message<byte[]> send = message(StompHeaderAccessor.create(StompCommand.SEND));
        assertThat(interceptor.preSend(send, channel)).isSameAs(send);
        Message<?> heartbeat = MessageBuilder.createMessage(new byte[0], StompHeaderAccessor.createForHeartbeat().getMessageHeaders());
        assertThat(interceptor.preSend(heartbeat, channel)).isSameAs(heartbeat);
        Message<String> plain = MessageBuilder.withPayload("x").build();
        assertThat(interceptor.preSend(plain, channel)).isSameAs(plain);
    }

    @Test
    void chatControllerDelegatesAndReportsErrors() {
        ChatWebSocketController controller = new ChatWebSocketController(tripService);
        UsernamePasswordAuthenticationToken principal =
                new UsernamePasswordAuthenticationToken(new AuthUser(7L, "+91", Set.of()), null);
        controller.chat(5L, new ChatMessageRequest("hello"), principal);
        verify(tripService).sendChat(5L, 7L, "hello");
        assertThat(controller.handleError(new IllegalStateException("nope"))).isEqualTo("nope");
    }
}
