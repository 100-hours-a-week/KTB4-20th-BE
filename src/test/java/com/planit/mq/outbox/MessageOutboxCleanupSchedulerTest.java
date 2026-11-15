package com.planit.mq.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageOutboxCleanupSchedulerTest {

    @DisplayName("현재 시각을 기준으로 Outbox를 정리한다")
    @Test
    void cleansUpAtCurrentTime() {
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 3, 0);
        MessageOutboxCleanup cleanup = mock(MessageOutboxCleanup.class);
        MessageOutboxCleanupScheduler scheduler = new MessageOutboxCleanupScheduler(
                cleanup,
                Clock.fixed(now.atZone(seoul).toInstant(), seoul)
        );

        scheduler.cleanup();

        verify(cleanup).cleanup(now);
    }

    @DisplayName("매주 월요일 03:00 KST에 실행하고, 락은 1시간 잡는다")
    @Test
    void runsEveryMondayAtThree() throws NoSuchMethodException {
        Method method = MessageOutboxCleanupScheduler.class.getDeclaredMethod("cleanup");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 3 * * MON");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertThat(lock.name()).isEqualTo("messageOutboxCleanup");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT1H");
    }
}
