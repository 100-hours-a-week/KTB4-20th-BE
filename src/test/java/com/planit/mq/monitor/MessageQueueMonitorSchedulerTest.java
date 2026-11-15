package com.planit.mq.monitor;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageQueueMonitorSchedulerTest {

    @DisplayName("주기마다 큐 감시를 실행한다")
    @Test
    void runsMonitor() {
        MessageQueueMonitor monitor = mock(MessageQueueMonitor.class);

        new MessageQueueMonitorScheduler(monitor).monitor();

        verify(monitor).monitor();
    }

    @DisplayName("5초 간격으로 실행하고, 락은 다음 주기 전에 풀리도록 4초로 잡는다")
    @Test
    void runsEveryFiveSecondsWithFourSecondLock() throws NoSuchMethodException {
        Method method = MessageQueueMonitorScheduler.class.getDeclaredMethod("monitor");

        assertThat(method.getAnnotation(Scheduled.class).fixedDelay()).isEqualTo(5000);
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertThat(lock.name()).isEqualTo("messageQueueMonitor");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT4S");
    }
}
