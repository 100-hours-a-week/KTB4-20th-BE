package com.planit.chat.websocket.subscription;

import com.planit.chat.presence.RegionalChatRoomPresenceRegistry;
import com.planit.chat.websocket.dto.ChatRoomSubscriptionResultEvent;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionalChatRoomSubscriptionChannelInterceptorTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private RegionalChatRoomRepository roomRepository;
    private RegionalChatRoomMemberRepository memberRepository;
    private RegionalChatRoomPresenceRegistry presenceRegistry;
    private SimpMessagingTemplate messagingTemplate;
    private RegionalChatRoomSubscriptionChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RegionalChatRoomRepository.class);
        memberRepository = mock(RegionalChatRoomMemberRepository.class);
        presenceRegistry = mock(RegionalChatRoomPresenceRegistry.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        channel = mock(MessageChannel.class);
        interceptor = new RegionalChatRoomSubscriptionChannelInterceptor(
                roomRepository,
                memberRepository,
                presenceRegistry,
                messagingTemplate
        );
    }

    @Test
    void acceptsSubscriptionForActiveMember() {
        when(roomRepository.existsById(3001L)).thenReturn(true);
        when(memberRepository.existsActiveMembership(
                UUID.fromString(USER_PUBLIC_ID),
                3001L
        )).thenReturn(true);
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/regional-chat-rooms/3001/messages",
                "subscription-1"
        );

        Message<?> result = interceptor.preSend(message, channel);
        interceptor.postSend(message, channel, true);

        assertThat(result).isSameAs(message);
        verify(presenceRegistry).register(
                3001L,
                USER_PUBLIC_ID,
                "session-1",
                "subscription-1"
        );
        verifySubscriptionResult("ACCEPTED", "CHAT_ROOM_SUBSCRIBED");
    }

    @Test
    void rejectsSubscriptionForNonMemberWithoutClosingConnection() {
        when(roomRepository.existsById(3001L)).thenReturn(true);
        when(memberRepository.existsActiveMembership(
                UUID.fromString(USER_PUBLIC_ID),
                3001L
        )).thenReturn(false);
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/regional-chat-rooms/3001/messages",
                "subscription-1"
        );

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isNull();
        verify(presenceRegistry, never()).register(any(), any(), any(), any());
        verifySubscriptionResult("REJECTED", "REGIONAL_CHAT_MEMBER_REQUIRED");
    }

    @Test
    void unregistersSubscription() {
        Message<byte[]> message = stompMessage(
                StompCommand.UNSUBSCRIBE,
                null,
                "subscription-1"
        );

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);

        verify(presenceRegistry).unregisterSubscription(
                "session-1",
                "subscription-1"
        );
    }

    @Test
    void ignoresPersonalEventSubscription() {
        Message<byte[]> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/user/queue/chat-events",
                "subscription-1"
        );

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);

        verify(roomRepository, never()).existsById(any());
        verify(presenceRegistry, never()).register(any(), any(), any(), any());
    }

    private Message<byte[]> stompMessage(
            StompCommand command,
            String destination,
            String subscriptionId
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        accessor.setUser(new TestingAuthenticationToken(USER_PUBLIC_ID, null));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @SuppressWarnings("unchecked")
    private void verifySubscriptionResult(String status, String code) {
        verify(messagingTemplate).convertAndSendToUser(
                eq(USER_PUBLIC_ID),
                eq("/queue/chat-events"),
                argThat(payload -> {
                    ChatRoomSubscriptionResultEvent event =
                            (ChatRoomSubscriptionResultEvent) payload;
                    return event.status().equals(status) && event.code().equals(code);
                }),
                any(Map.class)
        );
    }
}
