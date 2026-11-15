package com.planit.mq.monitor;

import com.planit.mq.job.AiJobType;
import com.planit.mq.monitor.RabbitManagementClient.QueueLookup;
import com.planit.mq.monitor.RabbitManagementClient.QueueStatus;
import com.planit.mq.topology.AiJobQueue;
import com.planit.mq.topology.AiResultQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageQueueMonitorTest {

    private static final QueueStatus EMPTY_WITH_CONSUMER = new QueueStatus(0, 1);

    private final RabbitManagementClient client = mock(RabbitManagementClient.class);
    private final MessageQueueMonitorStore store = mock(MessageQueueMonitorStore.class);
    private final MessageQueueAlert alert = mock(MessageQueueAlert.class);
    private final MessageQueueMonitor monitor = new MessageQueueMonitor(
            client,
            new QueueBlockPolicy(new MessageQueueMonitorProperties(
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
            )),
            store,
            alert
    );

    private Map<String, QueueLookup> lookups;

    @BeforeEach
    void setUp() {
        lookups = new LinkedHashMap<>();
        for (AiJobQueue queue : AiJobQueue.values()) {
            lookups.put(queue.taskQueue(), new QueueLookup.Found(EMPTY_WITH_CONSUMER));
            lookups.put(queue.dlq(), new QueueLookup.Found(EMPTY_WITH_CONSUMER));
        }
        lookups.put(AiResultQueue.DLQ, new QueueLookup.Found(new QueueStatus(0, 1)));
        when(client.getQueues(anyList())).thenAnswer(invocation -> lookups);
    }

    @DisplayName("작업 큐가 차면 해당 작업만 차단하고, 플래그를 새로 켰을 때만 경고한다")
    @Test
    void blocksFullTaskQueue() {
        QueueStatus full = new QueueStatus(3_300, 3);
        lookups.put(AiJobQueue.SCHEDULE_GENERATE.taskQueue(), new QueueLookup.Found(full));
        when(store.block(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE)).thenReturn(true, false);

        monitor.monitor();
        monitor.monitor();

        verify(store, times(2)).block(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE);
        verify(alert, times(1)).blocked(
                QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE, AiJobQueue.SCHEDULE_GENERATE.taskQueue(), full
        );
        verify(store, times(2)).unblock(QueueBlockType.TASK_QUEUE, AiJobType.PHOTO_JUDGE);
    }

    @DisplayName("요청 DLQ를 처리할 소비자가 없으면 DLQ 차단 플래그를 켠다")
    @Test
    void blocksDeadLetterQueueWithoutConsumer() {
        lookups.put(AiJobQueue.PHOTO_JUDGE.dlq(), new QueueLookup.Found(new QueueStatus(0, 0)));

        monitor.monitor();

        verify(store).block(QueueBlockType.DEAD_LETTER_QUEUE, AiJobType.PHOTO_JUDGE);
        verify(store).unblock(QueueBlockType.DEAD_LETTER_QUEUE, AiJobType.SCHEDULE_GENERATE);
    }

    @DisplayName("조회에 실패한 큐는 플래그를 바꾸지 않고, 실패가 시작될 때만 경고한다")
    @Test
    void keepsFlagWhenLookupFails() {
        String taskQueue = AiJobQueue.SCHEDULE_GENERATE.taskQueue();
        ResourceAccessException timeout = new ResourceAccessException("timeout");
        lookups.put(taskQueue, new QueueLookup.Failed(timeout));

        monitor.monitor();
        monitor.monitor();

        verify(store, never()).block(eq(QueueBlockType.TASK_QUEUE), eq(AiJobType.SCHEDULE_GENERATE));
        verify(store, never()).unblock(eq(QueueBlockType.TASK_QUEUE), eq(AiJobType.SCHEDULE_GENERATE));
        verify(alert, times(1)).lookupFailed(taskQueue, timeout);

        lookups.put(taskQueue, new QueueLookup.Found(EMPTY_WITH_CONSUMER));
        monitor.monitor();
        lookups.put(taskQueue, new QueueLookup.Failed(timeout));
        monitor.monitor();

        verify(alert, times(2)).lookupFailed(taskQueue, timeout);
    }

    @DisplayName("응답 DLQ에 메시지가 생기면 처음 한 번만 경고하고, 비면 상태를 지운다")
    @Test
    void alertsWhenResultDlqHasMessages() {
        QueueStatus notEmpty = new QueueStatus(2, 0);
        lookups.put(AiResultQueue.DLQ, new QueueLookup.Found(notEmpty));
        when(store.markResultDlqNotEmpty()).thenReturn(true, false);

        monitor.monitor();
        monitor.monitor();

        verify(alert, times(1)).resultDlqNotEmpty(AiResultQueue.DLQ, notEmpty);
        verify(store, never()).markResultDlqEmpty();

        lookups.put(AiResultQueue.DLQ, new QueueLookup.Found(new QueueStatus(0, 0)));
        monitor.monitor();

        verify(store).markResultDlqEmpty();
        verify(alert, never()).blocked(any(), any(), any(), any());
    }
}
