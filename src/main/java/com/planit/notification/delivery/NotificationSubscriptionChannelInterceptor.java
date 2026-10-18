package com.planit.notification.delivery;

import com.planit.websocket.subscription.StompSubscriptionReceiptEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSubscriptionChannelInterceptor
        implements ChannelInterceptor {

    private static final String DESTINATION = "/user/queue/notifications";

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void postSend(
            Message<?> message,
            MessageChannel channel,
            boolean sent
    ) {
        if (!sent) {
            return;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null
                || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                || !DESTINATION.equals(accessor.getDestination())
                || accessor.getReceipt() == null) {
            return;
        }
        eventPublisher.publishEvent(new StompSubscriptionReceiptEvent(
                accessor.getSessionId(),
                accessor.getReceipt()
        ));
    }
}
