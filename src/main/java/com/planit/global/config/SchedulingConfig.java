package com.planit.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    private static final String LOCK_ENVIRONMENT = "planit";

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
