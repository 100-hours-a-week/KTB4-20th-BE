package com.planit.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    private static final String LOCK_ENVIRONMENT = "planit";

    /**
     * 앱의 {@code @Scheduled} 메서드 수 이상으로 둔다. 메시지 큐 Relay(500ms)가 confirm을 기다리는 동안에도
     * 큐 감시(5초)나 Stale 검사 같은 다른 예약 작업이 밀리지 않게 하기 위함이다.
     * 기존 5개 + 메시지 큐 스케줄러 6개(Relay, Stale, 큐 감시, Outbox 정리, 포토미션 생성 접수, 05시 DLQ 재처리).
     */
    static final int POOL_SIZE = 11;

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("scheduled-task-");
        return scheduler;
    }

    @Bean
    public LockProvider lockProvider(
            RedisConnectionFactory connectionFactory
    ) {
        return new RedisLockProvider(
                connectionFactory,
                LOCK_ENVIRONMENT
        );
    }
}
