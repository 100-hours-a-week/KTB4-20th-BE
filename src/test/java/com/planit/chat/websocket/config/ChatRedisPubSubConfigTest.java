package com.planit.chat.websocket.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.listener.ChannelTopic;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRedisPubSubConfigTest {

    @DisplayName("오픈채팅 메시지는 알림과 분리된 전용 Redis 채널을 사용한다")
    @Test
    void createsDedicatedChatMessageTopic() {
        ChatRedisPubSubConfig config = new ChatRedisPubSubConfig();

        ChannelTopic topic = config.chatMessageTopic();

        assertThat(topic.getTopic()).isEqualTo("planit:chat:messages");
    }
}
