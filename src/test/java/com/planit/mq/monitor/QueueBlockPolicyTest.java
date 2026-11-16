package com.planit.mq.monitor;

import com.planit.mq.monitor.QueueBlockPolicy.QueueBlockDecision;
import com.planit.mq.monitor.RabbitManagementClient.QueueStatus;
import com.planit.mq.topology.AiJobQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QueueBlockPolicyTest {

    private final QueueBlockPolicy policy = new QueueBlockPolicy(new MessageQueueMonitorProperties(
            URI.create("http://localhost:15672"),
            "planit",
            "planit-monitor",
            "",
            Duration.ofMillis(300),
            Duration.ofMillis(500),
            0.95,
            0.50,
            0.90,
            0.50
    ));

    @DisplayName("작업 큐는 최대 길이의 95% 이상이면 차단, 50% 이하면 해제, 그 사이면 유지한다")
    @ParameterizedTest(name = "일정 생성 작업 큐 대기 {0}건 → {1}")
    @CsvSource({
            // 최대 길이 3,450 × 0.95 = 3,277.5
            "3278, BLOCK",
            "3277, KEEP",
            // 3,450 × 0.50 = 1,725
            "1726, KEEP",
            "1725, UNBLOCK",
            "0, UNBLOCK"
    })
    void decidesTaskQueue(long messagesReady, QueueBlockDecision expected) {
        QueueStatus status = new QueueStatus(messagesReady, 3);

        assertThat(policy.decideTaskQueue(AiJobQueue.SCHEDULE_GENERATE, status)).isEqualTo(expected);
    }

    @DisplayName("요청 DLQ는 최대 길이의 90% 이상이거나 소비자가 없으면 차단, 50% 이하이고 소비자가 있으면 해제한다")
    @ParameterizedTest(name = "일정 생성 DLQ 대기 {0}건, 소비자 {1} → {2}")
    @CsvSource({
            // 최대 길이 50,000 × 0.90 = 45,000
            "45000, 1, BLOCK",
            "44999, 1, KEEP",
            // 50,000 × 0.50 = 25,000
            "25000, 1, UNBLOCK",
            "0, 0, BLOCK",
            "30000, 0, BLOCK"
    })
    void decidesDeadLetterQueue(long messagesReady, int consumers, QueueBlockDecision expected) {
        QueueStatus status = new QueueStatus(messagesReady, consumers);

        assertThat(policy.decideDeadLetterQueue(AiJobQueue.SCHEDULE_GENERATE, status)).isEqualTo(expected);
    }

    @DisplayName("포토미션 생성 DLQ는 소비자가 없어도 개수 조건만 본다")
    @ParameterizedTest(name = "포토미션 생성 DLQ 대기 {0}건, 소비자 0 → {1}")
    @CsvSource({
            // 최대 길이 10,500 × 0.90 = 9,450
            "9450, BLOCK",
            "9449, KEEP",
            // 10,500 × 0.50 = 5,250
            "5250, UNBLOCK",
            "0, UNBLOCK"
    })
    void ignoresConsumersOfMissionGenerationDlq(long messagesReady, QueueBlockDecision expected) {
        QueueStatus status = new QueueStatus(messagesReady, 0);

        assertThat(policy.decideDeadLetterQueue(AiJobQueue.PHOTO_MISSION_GENERATE, status)).isEqualTo(expected);
    }
}
