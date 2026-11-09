package com.planit.mq.topology;

import java.util.List;

/**
 * AI 처리 결과를 받는 응답 큐 구성. 모든 작업이 하나의 응답 큐를 함께 쓴다.
 * 결과에는 AI 응답 본문이 들어 있으므로 개수 대신 크기로 제한한다.
 */
public final class AiResultQueue {

    public static final String TASK_QUEUE = "ai.result.task";
    public static final String ROUTING_KEY = "ai-result";
    public static final String DLQ = "ai.result.dlq";
    public static final String DLQ_ROUTING_KEY = "ai-result.dlq";

    /** Task, Retry, DLQ 공통 최대 크기 200MB */
    public static final int MAX_LENGTH_BYTES = 200 * 1024 * 1024;

    /** Result Retry Queue 단계별 대기 시간(ms). 단계는 1부터 센다. */
    public static final List<Integer> RETRY_TTL_MILLIS = List.of(1_000, 2_000, 4_000);

    private AiResultQueue() {
    }

    /** @param stage 1부터 시작하는 재시도 단계 */
    public static String retryQueue(int stage) {
        return "ai.result.retry." + (RETRY_TTL_MILLIS.get(stage - 1) / 1_000) + "s";
    }

    /** @param stage 1부터 시작하는 재시도 단계 */
    public static String retryRoutingKey(int stage) {
        return ROUTING_KEY + ".retry." + stage;
    }
}
