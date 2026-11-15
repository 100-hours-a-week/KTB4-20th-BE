package com.planit.mq.monitor;

import com.planit.mq.monitor.RabbitManagementClient.QueueStatus;
import com.planit.mq.topology.AiJobQueue;
import org.springframework.stereotype.Component;

/**
 * 큐 상태로 차단 플래그를 켤지 끌지 정한다. 차단 기준과 해제 기준 사이에서는 지금 상태를 유지한다.
 */
@Component
public class QueueBlockPolicy {

    private final MessageQueueMonitorProperties properties;

    public QueueBlockPolicy(MessageQueueMonitorProperties properties) {
        this.properties = properties;
    }

    public QueueBlockDecision decideTaskQueue(AiJobQueue queue, QueueStatus status) {
        if (isAtLeast(status, queue.maxLength(), properties.taskBlockRatio())) {
            return QueueBlockDecision.BLOCK;
        }
        if (isAtMost(status, queue.maxLength(), properties.taskUnblockRatio())) {
            return QueueBlockDecision.UNBLOCK;
        }
        return QueueBlockDecision.KEEP;
    }

    public QueueBlockDecision decideDeadLetterQueue(AiJobQueue queue, QueueStatus status) {
        boolean consumerMissing = requiresDlqConsumer(queue) && status.consumers() == 0;
        if (consumerMissing || isAtLeast(status, queue.dlqMaxLength(), properties.dlqBlockRatio())) {
            return QueueBlockDecision.BLOCK;
        }
        if (isAtMost(status, queue.dlqMaxLength(), properties.dlqUnblockRatio())) {
            return QueueBlockDecision.UNBLOCK;
        }
        return QueueBlockDecision.KEEP;
    }

    /** 포토미션 생성 DLQ는 소비자 없이 05시 재처리 스케줄러가 직접 꺼내므로 소비자 수를 보지 않는다. */
    private static boolean requiresDlqConsumer(AiJobQueue queue) {
        return queue != AiJobQueue.PHOTO_MISSION_GENERATE;
    }

    private static boolean isAtLeast(QueueStatus status, int maxLength, double ratio) {
        return status.messagesReady() >= maxLength * ratio;
    }

    private static boolean isAtMost(QueueStatus status, int maxLength, double ratio) {
        return status.messagesReady() <= maxLength * ratio;
    }

    public enum QueueBlockDecision {
        BLOCK,
        UNBLOCK,
        /** 차단 기준과 해제 기준 사이. 지금 상태를 유지한다. */
        KEEP
    }
}
