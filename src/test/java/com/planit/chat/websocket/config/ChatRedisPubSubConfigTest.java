package com.planit.chat.websocket.config;

import com.planit.chat.websocket.message.ChatMessageRedisSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatRedisPubSubConfigTest {

    @DisplayName("오픈채팅 메시지는 알림과 분리된 전용 Redis 채널을 사용한다")
    @Test
    void createsDedicatedChatMessageTopic() {
        ChatRedisPubSubConfig config = new ChatRedisPubSubConfig();

        ChannelTopic topic = config.chatMessageTopic();

        assertThat(topic.getTopic()).isEqualTo("planit:chat:messages");
    }

    @DisplayName("애플리케이션 Redis 연결을 사용하는 채팅 메시지 Listener Container를 구성한다")
    @Test
    void createsChatListenerContainerWithApplicationRedisConnection() {
        ChatRedisPubSubConfig config = new ChatRedisPubSubConfig();
        RedisConnectionFactory connectionFactory =
                mock(RedisConnectionFactory.class);
        ChatMessageRedisSubscriber subscriber =
                mock(ChatMessageRedisSubscriber.class);
        ChannelTopic topic = config.chatMessageTopic();

        RedisMessageListenerContainer container =
                config.chatMessageListenerContainer(
                        connectionFactory,
                        subscriber,
                        topic
                );

        assertThat(container.getConnectionFactory())
                .isSameAs(connectionFactory);
        assertThat(listenerTopics(container).get(subscriber))
                .containsExactly(topic);
        FixedBackOff recoveryBackOff = (FixedBackOff) ReflectionTestUtils
                .getField(container, "backOff");
        assertThat(recoveryBackOff.getInterval()).isEqualTo(1_000L);
    }

    @SuppressWarnings("unchecked")
    private Map<MessageListener, Set<Topic>> listenerTopics(
            RedisMessageListenerContainer container
    ) {
        return (Map<MessageListener, Set<Topic>>) ReflectionTestUtils.getField(
                container,
                "listenerTopics"
        );
    }
}
