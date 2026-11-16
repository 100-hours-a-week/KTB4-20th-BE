package com.planit.mq.topology;

import java.util.List;

/**
 * AI 요청 작업별 큐 구성.
 * 이름과 값은 RabbitMQ 인프라·메시지 계약 문서 3절과 같아야 한다.
 */
public enum AiJobQueue {

    SCHEDULE_GENERATE("ai.schedule-generation", "ai-schedule-generation", 3_450, 50_000),
    PHOTO_MISSION_GENERATE("ai.photomission-generation", "ai-photomission-generation", 10_500, 10_500),
    PHOTO_JUDGE("ai.photomission-verification", "ai-photomission-verification", 100, 50_000);

    /** 재전달 최대 횟수. 넘으면 RabbitMQ가 DLQ로 dead-letter한다. */
    public static final int DELIVERY_LIMIT = 5;

    /** Retry Queue 단계별 대기 시간(ms). 단계는 1부터 센다. */
    public static final List<Integer> RETRY_TTL_MILLIS = List.of(5_000, 10_000, 20_000);

    private final String queuePrefix;
    private final String routingKey;
    private final int maxLength;
    private final int dlqMaxLength;

    AiJobQueue(String queuePrefix, String routingKey, int maxLength, int dlqMaxLength) {
        this.queuePrefix = queuePrefix;
        this.routingKey = routingKey;
        this.maxLength = maxLength;
        this.dlqMaxLength = dlqMaxLength;
    }

    public String taskQueue() {
        return queuePrefix + ".task";
    }

    /** @param stage 1부터 시작하는 재시도 단계 */
    public String retryQueue(int stage) {
        return queuePrefix + ".retry." + (RETRY_TTL_MILLIS.get(stage - 1) / 1_000) + "s";
    }

    /** @param stage 1부터 시작하는 재시도 단계 */
    public String retryRoutingKey(int stage) {
        return routingKey + ".retry." + stage;
    }

    public String dlq() {
        return queuePrefix + ".dlq";
    }

    public String dlqRoutingKey() {
        return routingKey + ".dlq";
    }

    public String routingKey() {
        return routingKey;
    }

    /** Task Queue와 Retry Queue가 함께 쓰는 최대 메시지 수 */
    public int maxLength() {
        return maxLength;
    }

    public int dlqMaxLength() {
        return dlqMaxLength;
    }
}
