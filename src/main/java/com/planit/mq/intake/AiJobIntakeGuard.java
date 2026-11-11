package com.planit.mq.intake;

import com.planit.mq.job.AiJobType;
import com.planit.mq.monitor.QueueBlockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * AI 작업 요청을 받을 때 Redis 플래그를 확인한다. 플래그는 요청마다 읽으므로 재배포 없이 바로 반영된다.
 * 전환 플래그 planit:mq:async:{jobType}: 값이 on이면 메시지 큐로 접수하고, 아니면 기존 동기 경로를 탄다.
 * 차단 플래그 planit:mq:block:{taskqueue|deadletterqueue}:{jobType}: 하나라도 있으면 저장하지 않고 503으로 응답한다.
 */
@Component
public class AiJobIntakeGuard {

    private static final Logger log = LoggerFactory.getLogger(AiJobIntakeGuard.class);

    private static final String ASYNC_KEY_FORMAT = "planit:mq:async:%s";
    private static final String ASYNC_ON = "on";

    private final StringRedisTemplate redisTemplate;
    private final AiJobIntakeProperties properties;

    public AiJobIntakeGuard(StringRedisTemplate redisTemplate, AiJobIntakeProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Redis 조회에 실패하면 기존 동기 경로로 처리한다. 전환 전 상태이자 플래그를 지워 롤백한 상태와 같다.
     *
     * @return 메시지 큐로 접수해야 하면 true
     */
    public boolean isAsync(AiJobType jobType) {
        String key = ASYNC_KEY_FORMAT.formatted(jobType.name());
        try {
            return ASYNC_ON.equals(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException e) {
            log.warn("AI 작업 전환 플래그 조회 실패, 기존 동기 경로로 처리함 key={}", key, e);
            return false;
        }
    }

    /**
     * 작업 행과 Outbox를 저장하기 전에 호출한다. Redis 조회에 실패하면 차단하지 않은 것으로 본다.
     * 큐가 실제로 가득 차 있으면 발행이 거부되고 Outbox 재시도로 이어지므로 접수한 작업은 유실되지 않는다.
     *
     * @throws AiJobTemporarilyUnavailableException 작업 큐나 요청 DLQ 차단 플래그가 켜져 있으면
     */
    public void requireAvailable(AiJobType jobType) {
        if (isBlocked(jobType)) {
            throw new AiJobTemporarilyUnavailableException(properties.retryAfter());
        }
    }

    private boolean isBlocked(AiJobType jobType) {
        List<String> keys = Arrays.stream(QueueBlockType.values())
                .map(type -> type.key(jobType))
                .toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            return values != null && values.stream().anyMatch(Objects::nonNull);
        } catch (RuntimeException e) {
            log.warn("AI 작업 차단 플래그 조회 실패, 차단하지 않고 접수함 keys={}", keys, e);
            return false;
        }
    }
}
