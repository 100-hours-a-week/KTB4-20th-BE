package com.planit.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchedulingConfigIntegrationTest {

    @Autowired
    private List<LockProvider> lockProviders;

    @DisplayName("모든 스케줄러는 하나의 Redis 락 공급자를 공유한다")
    @Test
    void sharesSingleRedisLockProvider() {
        assertThat(lockProviders)
                .singleElement()
                .isInstanceOf(RedisLockProvider.class);
    }
}
