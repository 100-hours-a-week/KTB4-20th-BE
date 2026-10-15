package com.planit.notification.precheck;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class TripRemindSchedulingConfig {

    private static final String LOCK_ENVIRONMENT = "planit";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public LockProvider lockProvider(
            RedisConnectionFactory connectionFactory
    ) {
        return new RedisLockProvider(
                connectionFactory,
                LOCK_ENVIRONMENT
        );
    }

    @Bean
    public Clock tripRemindClock() {
        return Clock.system(SEOUL_ZONE);
    }
}
