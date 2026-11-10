package com.planit.mq.monitor;

import com.planit.mq.job.AiJobType;
import com.planit.mq.monitor.QueueBlockPolicy.QueueBlockDecision;
import com.planit.mq.monitor.RabbitManagementClient.QueueLookup;
import com.planit.mq.monitor.RabbitManagementClient.QueueStatus;
import com.planit.mq.topology.AiJobQueue;
import com.planit.mq.topology.AiResultQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 작업 큐, 요청 DLQ, 응답 DLQ를 조회해 차단 플래그를 켜고 끈다.
 * 조회에 실패한 큐는 플래그를 바꾸지 않는다.
 */
@Service
public class MessageQueueMonitor {

    private static final Logger log = LoggerFactory.getLogger(MessageQueueMonitor.class);

    private final RabbitManagementClient client;
    private final QueueBlockPolicy policy;
    private final MessageQueueMonitorStore store;
    private final MessageQueueAlert alert;

    /**
     * 조회가 실패 중인 큐. 실패가 시작될 때만 경고한다.
     * 조회 실패는 인스턴스의 네트워크 문제일 수 있어 Redis가 아니라 인스턴스마다 따로 기억한다.
     */
    private final Set<String> failingQueues = ConcurrentHashMap.newKeySet();

    public MessageQueueMonitor(
            RabbitManagementClient client,
            QueueBlockPolicy policy,
            MessageQueueMonitorStore store,
            MessageQueueAlert alert
    ) {
        this.client = client;
        this.policy = policy;
        this.store = store;
        this.alert = alert;
    }

    public void monitor() {
        Map<String, QueueLookup> lookups = client.getQueues(monitoredQueues());
        for (AiJobQueue queue : AiJobQueue.values()) {
            statusOf(lookups, queue.taskQueue()).ifPresent(status -> apply(
                    QueueBlockType.TASK_QUEUE, queue, queue.taskQueue(), status,
                    policy.decideTaskQueue(queue, status)
            ));
            statusOf(lookups, queue.dlq()).ifPresent(status -> apply(
                    QueueBlockType.DEAD_LETTER_QUEUE, queue, queue.dlq(), status,
                    policy.decideDeadLetterQueue(queue, status)
            ));
        }
        statusOf(lookups, AiResultQueue.DLQ).ifPresent(this::checkResultDlq);
    }

    private static List<String> monitoredQueues() {
        List<String> queues = new ArrayList<>();
        for (AiJobQueue queue : AiJobQueue.values()) {
            queues.add(queue.taskQueue());
            queues.add(queue.dlq());
        }
        queues.add(AiResultQueue.DLQ);
        return queues;
    }

    private Optional<QueueStatus> statusOf(Map<String, QueueLookup> lookups, String queueName) {
        return switch (lookups.get(queueName)) {
            case QueueLookup.Found found -> {
                if (failingQueues.remove(queueName)) {
                    log.info("큐 상태 조회 복구 queue={}", queueName);
                }
                yield Optional.of(found.status());
            }
            case QueueLookup.Failed failed -> {
                if (failingQueues.add(queueName)) {
                    alert.lookupFailed(queueName, failed.cause());
                }
                yield Optional.empty();
            }
        };
    }

    private void apply(
            QueueBlockType type,
            AiJobQueue queue,
            String queueName,
            QueueStatus status,
            QueueBlockDecision decision
    ) {
        AiJobType jobType = jobTypeOf(queue);
        switch (decision) {
            case BLOCK -> {
                if (store.block(type, jobType)) {
                    alert.blocked(type, jobType, queueName, status);
                }
            }
            case UNBLOCK -> {
                if (store.unblock(type, jobType)) {
                    log.info("AI 작업 요청 차단 해제 type={}, jobType={}, queue={}, messagesReady={}, consumers={}",
                            type, jobType, queueName, status.messagesReady(), status.consumers());
                }
            }
            case KEEP -> {
            }
        }
    }

    private void checkResultDlq(QueueStatus status) {
        if (status.messagesReady() == 0) {
            store.markResultDlqEmpty();
            return;
        }
        if (store.markResultDlqNotEmpty()) {
            alert.resultDlqNotEmpty(AiResultQueue.DLQ, status);
        }
    }

    private static AiJobType jobTypeOf(AiJobQueue queue) {
        return switch (queue) {
            case SCHEDULE_GENERATE -> AiJobType.SCHEDULE_GENERATE;
            case PHOTO_MISSION_GENERATE -> AiJobType.PHOTO_MISSION_GENERATE;
            case PHOTO_JUDGE -> AiJobType.PHOTO_JUDGE;
        };
    }
}
