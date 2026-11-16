package com.planit.mq.dlq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiDlqListenerConfigTest {

    @DisplayName("요청 DLQ Listener는 Consumer 1개, prefetch 기본값, 수동 ACK이고 거부한 메시지를 다시 넣지 않는다")
    @Test
    void configuresDlqListenerFactory() {
        SimpleRabbitListenerContainerFactory factory =
                new AiDlqListenerConfig().dlqListenerFactory(mock(ConnectionFactory.class));

        assertThat(ReflectionTestUtils.getField(factory, "concurrentConsumers")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(factory, "maxConcurrentConsumers")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isNull();
        assertThat(ReflectionTestUtils.getField(factory, "acknowledgeMode")).isEqualTo(AcknowledgeMode.MANUAL);
        assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(false);
    }
}
