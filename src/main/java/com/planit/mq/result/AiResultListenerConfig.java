package com.planit.mq.result;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiResultListenerConfig {

    public static final String RESULT_LISTENER_FACTORY = "resultListenerFactory";

    /** 결과 하나를 반영하는 동안 DB 커넥션을 하나 쓴다. HikariCP 기본 10개를 과하게 쓰지 않도록 4개로 둔다. */
    static final int CONSUMERS = 4;
    static final int PREFETCH = 20;

    /**
     * 응답 큐 Listener. 결과를 커밋한 뒤, 또는 재시도·DLQ 재발행 confirm을 받은 뒤에 직접 ACK한다.
     * 처리하지 못한 예외로 거부할 때 큐에 다시 넣지 않고 RabbitMQ 재전달 한도에 맡긴다.
     */
    @Bean(name = RESULT_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory resultListenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(CONSUMERS);
        factory.setMaxConcurrentConsumers(CONSUMERS);
        factory.setPrefetchCount(PREFETCH);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
