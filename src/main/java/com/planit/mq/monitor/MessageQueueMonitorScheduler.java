package com.planit.mq.monitor;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MessageQueueMonitorScheduler {

    private final MessageQueueMonitor monitor;

    public MessageQueueMonitorScheduler(MessageQueueMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * 큐를 동시에 조회하므로 한 번의 감시는 조회 대기 시간(최대 약 0.8초) 안에 끝난다.
     * 락은 다음 주기(5초) 전에 풀리도록 4초로 잡는다. 락이 잡혀 있으면 그 주기는 건너뛴다.
     */
    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "messageQueueMonitor", lockAtMostFor = "PT4S")
    public void monitor() {
        monitor.monitor();
    }
}
