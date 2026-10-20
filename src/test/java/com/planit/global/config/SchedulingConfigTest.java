package com.planit.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulingConfigTest {

    @DisplayName("일반 예약 작업에 사용할 기본 TaskScheduler를 구성한다")
    @Test
    void configuresDefaultTaskScheduler() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    RedisConnectionFactory.class,
                    () -> mock(RedisConnectionFactory.class)
            );
            context.register(SchedulingConfig.class);
            context.refresh();

            ThreadPoolTaskScheduler scheduler = context.getBean(
                    "taskScheduler",
                    ThreadPoolTaskScheduler.class
            );

            assertThat(scheduler.getScheduledThreadPoolExecutor()
                    .getCorePoolSize()).isEqualTo(4);
            assertThat(scheduler.getThreadNamePrefix())
                    .isEqualTo("scheduled-task-");
        }
    }
}
