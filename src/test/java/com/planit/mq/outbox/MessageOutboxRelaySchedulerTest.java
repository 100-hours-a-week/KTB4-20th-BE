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

class MessageOutboxRelaySchedulerTest {

    @DisplayName("현재 시각을 기준으로 발행할 차례가 된 메시지를 다시 발행한다")
    @Test
    void relaysAtCurrentTime() {
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 10, 0);
        MessageOutboxRelay relay = mock(MessageOutboxRelay.class);
        MessageOutboxRelayScheduler scheduler = new MessageOutboxRelayScheduler(
                relay,
                Clock.fixed(now.atZone(seoul).toInstant(), seoul)
        );

        scheduler.relay();

        verify(relay).relayDue(now);
    }

    @DisplayName("500ms 간격으로 실행하고, 락은 confirm 대기시간보다 긴 10초로 잡는다")
    @Test
    void runsEvery500msWithTenSecondLock() throws NoSuchMethodException {
        Method method = MessageOutboxRelayScheduler.class.getDeclaredMethod("relay");

        assertThat(method.getAnnotation(Scheduled.class).fixedDelay()).isEqualTo(500);
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertThat(lock.name()).isEqualTo("messageOutboxRelay");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT10S");
    }
}
