package com.planit.chat.websocket.config;

import com.planit.chat.websocket.message.ChatMessageRedisSubscriber;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
public class ChatRedisPubSubConfig {

    private static final String CHAT_MESSAGE_CHANNEL =
            "planit:chat:messages";
    private static final long CHAT_SUBSCRIPTION_RECOVERY_INTERVAL_MILLIS =
            1_000L;

    @Bean
    public ChannelTopic chatMessageTopic() {
        return ChannelTopic.of(CHAT_MESSAGE_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer chatMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatMessageRedisSubscriber subscriber,
            @Qualifier("chatMessageTopic") ChannelTopic chatMessageTopic
    ) {
        RedisMessageListenerContainer container =
                new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(
                CHAT_SUBSCRIPTION_RECOVERY_INTERVAL_MILLIS
        );
        container.addMessageListener(subscriber, chatMessageTopic);
        return container;
    }
}
