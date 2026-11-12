package com.planit.mq.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiResultListenerConfigTest {

    @DisplayName("응답 큐 Listener는 Consumer 4개, prefetch 20, 수동 ACK이고 거부한 메시지를 다시 넣지 않는다")
    @Test
    void configuresResultListenerFactory() {
        SimpleRabbitListenerContainerFactory factory =
                new AiResultListenerConfig().resultListenerFactory(mock(ConnectionFactory.class));

        assertThat(ReflectionTestUtils.getField(factory, "concurrentConsumers")).isEqualTo(4);
        assertThat(ReflectionTestUtils.getField(factory, "maxConcurrentConsumers")).isEqualTo(4);
        assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isEqualTo(20);
        assertThat(ReflectionTestUtils.getField(factory, "acknowledgeMode")).isEqualTo(AcknowledgeMode.MANUAL);
        assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(false);
    }
}
