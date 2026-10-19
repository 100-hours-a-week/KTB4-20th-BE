package com.planit.chat.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;

@Configuration(proxyBeanMethods = false)
public class ChatRedisPubSubConfig {

    private static final String CHAT_MESSAGE_CHANNEL =
            "planit:chat:messages";

    @Bean
    public ChannelTopic chatMessageTopic() {
        return ChannelTopic.of(CHAT_MESSAGE_CHANNEL);
    }
}
