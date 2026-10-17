package com.planit.notification.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    static final String CHANNEL = "planit:notifications";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(NotificationPublishEvent event) {
        Objects.requireNonNull(event, "발행할 알림 이벤트는 필수입니다");
        redisTemplate.convertAndSend(
                CHANNEL,
                objectMapper.writeValueAsString(event)
        );
    }
}
