package com.planit.chat.websocket.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatSubscriptionReceiptSenderTest {

    @Test
    void sendsReceiptToRequestingSession() {
        MessageChannel outboundChannel = mock(MessageChannel.class);
        ChatSubscriptionReceiptSender sender =
                new ChatSubscriptionReceiptSender(outboundChannel);

        sender.send(new ChatSubscriptionReceiptEvent(
                "session-1",
                "subscribe-3001"
        ));

        var captor = forClass(Message.class);
        verify(outboundChannel).send(captor.capture());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(captor.getValue());
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.RECEIPT);
        assertThat(accessor.getSessionId()).isEqualTo("session-1");
        assertThat(accessor.getReceiptId()).isEqualTo("subscribe-3001");
    }
}
