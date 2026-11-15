package com.planit.mq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

    public static final String CONFIRM_EXECUTOR = "mqConfirmExecutor";
    public static final String MQ_CLOCK = "mqClock";

    /** 발행 시각, 재시도 시각 등 메시지 큐 처리에서 쓰는 시각. DB의 다른 시각과 같이 서울 시간으로 저장한다. */
    @Bean(name = MQ_CLOCK)
    public Clock mqClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    /**
     * Publisher confirm 결과를 반영할 실행기.
     * confirm은 Spring AMQP 연결 스레드가 완료하므로, 그 스레드에서 DB 작업을 하면 다음 confirm 처리가 막힌다.
     */
    @Bean(name = CONFIRM_EXECUTOR, destroyMethod = "close")
    public ExecutorService mqConfirmExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
