package com.planit.mq.monitor;

import com.planit.mq.job.AiJobType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 큐 감시 결과를 Redis에 저장한다. 감시는 ShedLock을 잡은 인스턴스가 번갈아 실행하므로
 * 상태가 바뀌었는지는 인스턴스 메모리가 아니라 Redis 값으로 판단한다.
 */
@Component
public class MessageQueueMonitorStore {

    private static final String RESULT_DLQ_ALERTED_KEY = "planit:mq:alert:resultdlq";
    private static final String FLAG_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    public MessageQueueMonitorStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** @return 이번 호출로 차단 플래그를 새로 켰으면 true */
    public boolean block(QueueBlockType type, AiJobType jobType) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(type.key(jobType), FLAG_VALUE));
    }

    /** @return 이번 호출로 차단 플래그를 껐으면 true */
    public boolean unblock(QueueBlockType type, AiJobType jobType) {
        return Boolean.TRUE.equals(redisTemplate.delete(type.key(jobType)));
    }

    /** @return 응답 DLQ가 비어 있다가 이번에 메시지가 생긴 것이면 true */
    public boolean markResultDlqNotEmpty() {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(RESULT_DLQ_ALERTED_KEY, FLAG_VALUE));
    }

    public void markResultDlqEmpty() {
        redisTemplate.delete(RESULT_DLQ_ALERTED_KEY);
    }
}
