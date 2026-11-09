package com.planit.mq.topology;

/** AI 작업 메시지가 사용하는 exchange 이름. 모두 direct 타입이다. */
public final class AiExchange {

    /** 작업 메시지를 작업별 Task Queue로 보낸다. Retry Queue에서 TTL이 끝난 요청도 여기로 돌아온다. */
    public static final String REQUEST = "ai.request";

    /** 실패한 메시지를 다음 단계 Retry Queue로 보낸다. */
    public static final String RETRY = "ai.retry";

    /** 재시도를 모두 쓰거나 처리할 수 없는 메시지를 DLQ로 보낸다. */
    public static final String DLX = "ai.dlx";

    /** AI 처리 결과를 응답 큐로 보낸다. Result Retry Queue에서 TTL이 끝난 결과도 여기로 돌아온다. */
    public static final String RESULT = "ai.result";

    private AiExchange() {
    }
}
