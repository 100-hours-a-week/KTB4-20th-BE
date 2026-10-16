package com.planit.notification.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class NotificationEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        NotificationPublishEvent event = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8),
                NotificationPublishEvent.class
        );
        applicationEventPublisher.publishEvent(event);
    }
}
