package com.planit.chat.websocket.subscription;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class ChatSubscriptionReceiptSender {

    private final MessageChannel clientOutboundChannel;

    public ChatSubscriptionReceiptSender(
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel
    ) {
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @EventListener
    public void send(ChatSubscriptionReceiptEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.RECEIPT);
        accessor.setSessionId(event.sessionId());
        accessor.setReceiptId(event.receiptId());
        accessor.setLeaveMutable(true);
        clientOutboundChannel.send(MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        ));
    }
}
