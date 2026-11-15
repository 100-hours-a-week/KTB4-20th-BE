package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class MessageOutboxRelayScheduler {

    private final MessageOutboxRelay relay;
    private final Clock clock;

    public MessageOutboxRelayScheduler(
            MessageOutboxRelay relay,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.relay = relay;
        this.clock = clock;
    }

    /**
     * 한 틱이 confirm을 최대 confirm 대기시간(5초)만큼 기다리므로 락은 그보다 길게 잡는다.
     * 락이 잡혀 있으면 그 틱은 건너뛴다.
     */
    @Scheduled(fixedDelay = 500)
    @SchedulerLock(name = "messageOutboxRelay", lockAtMostFor = "PT10S")
    public void relay() {
        relay.relayDue(LocalDateTime.now(clock));
    }
}
