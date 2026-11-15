package com.planit.mq.dlq;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiDlqListenerConfig {

    public static final String DLQ_LISTENER_FACTORY = "dlqListenerFactory";

    /**
     * 요청 DLQ는 장애 때만 쌓이고 한 건 처리가 조건부 UPDATE와 알림 저장 한 트랜잭션이라 Consumer 1개로 충분하다.
     * DB 커넥션도 하나만 쓴다. prefetch는 Spring AMQP 기본값을 쓴다.
     * 작업을 실패로 커밋한 뒤 직접 ACK하고, 처리하지 못한 예외로 거부할 때 큐에 다시 넣지 않는다.
     */
    @Bean(name = DLQ_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory dlqListenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
