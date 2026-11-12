package com.planit.mq.monitor;

import com.planit.mq.job.AiJobType;
import com.planit.mq.monitor.RabbitManagementClient.QueueStatus;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 큐 감시 경고를 로그와 Sentry로 보낸다. 같은 상태가 이어지는 동안 반복해서 부르지 않는 것은 호출한 쪽이 맡는다. */
@Component
public class MessageQueueAlert {

    private static final Logger log = LoggerFactory.getLogger(MessageQueueAlert.class);

    public void blocked(QueueBlockType type, AiJobType jobType, String queueName, QueueStatus status) {
        warn("AI 작업 요청 차단 type=%s, jobType=%s, queue=%s, messagesReady=%d, consumers=%d".formatted(
                type, jobType, queueName, status.messagesReady(), status.consumers()
        ));
    }

    public void resultDlqNotEmpty(String queueName, QueueStatus status) {
        warn("응답 DLQ에 메시지가 쌓임 queue=%s, messagesReady=%d".formatted(queueName, status.messagesReady()));
    }

    public void lookupFailed(String queueName, Throwable cause) {
        String message = "큐 상태 조회 실패, 차단 플래그를 유지함 queue=%s".formatted(queueName);
        log.warn(message, cause);
        Sentry.captureMessage(message + ", cause=" + cause, SentryLevel.WARNING);
    }

    private static void warn(String message) {
        log.warn(message);
        Sentry.captureMessage(message, SentryLevel.WARNING);
    }
}
