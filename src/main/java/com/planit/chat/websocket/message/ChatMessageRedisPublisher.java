package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Component
public class ChatMessageRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic chatMessageTopic;

    public ChatMessageRedisPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("chatMessageTopic") ChannelTopic chatMessageTopic
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatMessageTopic = chatMessageTopic;
    }

    public void publish(ChatMessageCreatedEvent event) {
        Objects.requireNonNull(event, "발행할 채팅 메시지 이벤트는 필수입니다");
        redisTemplate.convertAndSend(
                chatMessageTopic.getTopic(),
                objectMapper.writeValueAsString(event)
        );
    }
}
