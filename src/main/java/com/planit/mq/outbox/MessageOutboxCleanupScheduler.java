package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class MessageOutboxCleanupScheduler {

    private final MessageOutboxCleanup cleanup;
    private final Clock clock;

    public MessageOutboxCleanupScheduler(
            MessageOutboxCleanup cleanup,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.cleanup = cleanup;
        this.clock = clock;
    }

    /** 1주일 분량(최대 수십만 행)도 1,000건씩 수백 번 반복으로 락 1시간 안에 끝난다. */
    @Scheduled(cron = "0 0 3 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "messageOutboxCleanup", lockAtMostFor = "PT1H")
    public void cleanup() {
        cleanup.cleanup(LocalDateTime.now(clock));
    }
}
